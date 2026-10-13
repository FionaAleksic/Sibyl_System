#!/usr/bin/env bash
# Rocky Linux 10 Sibyl frontend, from an explicitly checksum-pinned GitHub CI artifact.
# Example: FRONTEND_ZIP=/root/sibyl-frontend-v2-dist.zip FRONTEND_SHA256=<sha256> bash install-frontend.sh
set -Eeuo pipefail
umask 077
[[ ${EUID} -eq 0 ]] || { echo "Run as root" >&2; exit 1; }
source /etc/os-release
[[ "${ID}" == rocky && "${VERSION_ID%%.*}" == 10 ]] || { echo "Rocky Linux 10 required" >&2; exit 1; }

archive="${FRONTEND_ZIP:-}"
digest="${FRONTEND_SHA256:-}"
[[ -f "$archive" && "$digest" =~ ^[a-fA-F0-9]{64}$ ]] || {
  echo "Supply FRONTEND_ZIP and its exact sha256 from a successful GitHub Actions run" >&2; exit 1;
}
[[ "$(sha256sum "$archive" | cut -d' ' -f1)" == "${digest,,}" ]] || {
  echo "Frontend artifact SHA256 mismatch" >&2; exit 1;
}
backend="${SIBYL_BACKEND_IP:-172.22.120.248}"
[[ "$backend" =~ ^172\.22\.120\.[0-9]{1,3}$ ]] || {
  echo "Backend IP outside authorized lab" >&2; exit 1;
}
listen="${SIBYL_FRONTEND_IP:-172.22.120.247}"
[[ "$listen" =~ ^172\.22\.120\.[0-9]{1,3}$ ]] || {
  echo "Frontend IP outside authorized lab" >&2; exit 1;
}
manager="${SIBYL_ADMIN_IP:-172.22.100.118}"
[[ "$manager" =~ ^172\.22\.100\.[0-9]{1,3}$ ]] || {
  echo "Admin IP outside authorized management network" >&2; exit 1;
}

dnf -y install nginx openssl python3 firewalld policycoreutils-python-utils
systemctl enable --now firewalld
release="${FRONTEND_RELEASE_ID:-manual-verified-artifact}"
[[ "$release" =~ ^[A-Za-z0-9._-]{3,90}$ ]] || { echo "Invalid release ID" >&2; exit 1; }
install -d -m 0755 /srv/sibyl/frontend-v2/releases/"$release"
python3 - "$archive" "/srv/sibyl/frontend-v2/releases/$release" <<'PY'
import pathlib, sys, zipfile
source, target = sys.argv[1:]
with zipfile.ZipFile(source) as archive:
    names = archive.namelist()
    if "index.html" not in names or not any(n.startswith("assets/") for n in names):
        raise SystemExit("Missing Vite index.html or assets/")
    if any(n.startswith("/") or ".." in pathlib.PurePosixPath(n).parts for n in names):
        raise SystemExit("Unsafe ZIP paths")
    archive.extractall(target)
PY
# ZipFile.extractall respects root umask 0077; make public static files readable by nginx.
chmod -R a+rX "/srv/sibyl/frontend-v2/releases/$release"
ln -sfn "/srv/sibyl/frontend-v2/releases/$release" /srv/sibyl/frontend-v2/current
# Default SELinux labels on /srv can deny Nginx static asset reads.
semanage fcontext -a -t httpd_sys_content_t '/srv/sibyl/frontend-v2(/.*)?' 2>/dev/null || \
  semanage fcontext -m -t httpd_sys_content_t '/srv/sibyl/frontend-v2(/.*)?'
restorecon -RF /srv/sibyl/frontend-v2
install -d -m 0700 /etc/sibyl/tls
if [[ ! -s /etc/sibyl/tls/lab.crt || ! -s /etc/sibyl/tls/lab.key ]]; then
  openssl req -new -x509 -nodes -sha256 -newkey rsa:3072 -days 30 \
    -keyout /etc/sibyl/tls/lab.key -out /etc/sibyl/tls/lab.crt \
    -subj "/CN=sibyl-rocky10-frontend" -addext "subjectAltName=IP:$listen"
fi
chmod 0600 /etc/sibyl/tls/lab.key

cat >/etc/nginx/conf.d/sibyl-v2.conf <<NGINX
server {
    listen ${listen}:443 ssl;
    server_name _;
    server_tokens off;
    root /srv/sibyl/frontend-v2/current;
    index index.html;
    ssl_certificate /etc/sibyl/tls/lab.crt;
    ssl_certificate_key /etc/sibyl/tls/lab.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    allow ${manager};
    deny all;
    add_header X-Content-Type-Options nosniff always;
    add_header X-Frame-Options DENY always;
    add_header Referrer-Policy no-referrer always;
    add_header Cache-Control "no-store" always;

    # The login page is rendered by Sibyl Core, but shares the frontend design system.
    # An exact location keeps only its stylesheet local; login processing stays on Core.
    location = /login.css {
        try_files /login.css =404;
    }
    location = /healthz {
        default_type text/plain;
        return 200 "sibyl rocky10 frontend active\\n";
    }
    # Native Core pages (Admin and Addon Browser) load protected CSS/JS from backend.
    location ~ ^/(sibyl\.(css|js)|styles\.css|addon-browser\.js|module\.js)$ {
        proxy_pass http://${backend}:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }
    location ^~ /api/ {
        proxy_pass http://${backend}:8080;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_connect_timeout 3s;
        proxy_read_timeout 30s;
    }
    location ~ ^/(login\\.html|login\\.css|login\\.js|admin\\.html|admin\\.js|addons\\.html|addons\\.js|module\\.html|extension-host\\.html|login|logout)$ {
        proxy_pass http://${backend}:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Forwarded-Proto https;
    }
    location / { try_files \$uri \$uri/ /index.html; }
}
NGINX
semanage fcontext -a -t cert_t '/etc/sibyl/tls(/.*)?' 2>/dev/null || \
  semanage fcontext -m -t cert_t '/etc/sibyl/tls(/.*)?'
restorecon -RF /etc/sibyl/tls
# Do not disable SELinux globally. Grant Nginx permission to reverse-proxy internally.
setsebool -P httpd_can_network_connect 1
nginx -t
systemctl enable --now nginx
systemctl reload nginx
firewall-cmd --permanent --add-rich-rule="rule family=ipv4 source address=$manager/32 port port=443 protocol=tcp accept"
firewall-cmd --permanent --remove-service=cockpit || true
firewall-cmd --reload
echo "HTTPS frontend installed on $listen; ACL permits only $manager until admin password rotation and testing."
echo "Certificate is temporary/self-signed. Backend must be running on $backend:8080."
