#!/usr/bin/env bash
# Cluster-3 Sibyl Edge: never serve an unauthenticated HTTP preview.
# Does not edit SSH, proxy destinations or existing TLS certificates.
set -euo pipefail
[[ "$(id -u)" -eq 0 && "$(hostname)" == "sibyl-edge-lab" ]] || exit 2
p=/etc/nginx/sites-available/sibyl-lab.conf
[[ -f "$p" ]] || { echo "Expected Nginx site missing" >&2; exit 2; }
backup="$p.before-login-only-$(date -u +%Y%m%dT%H%M%SZ)"
cp -a "$p" "$backup"
rollback() {
  if [[ "$?" != 0 ]]; then
    cp -a "$backup" "$p"
    nginx -t && systemctl reload nginx || true
  fi
}
trap rollback EXIT
python3 - <<'PY'
from pathlib import Path
p=Path('/etc/nginx/sites-available/sibyl-lab.conf')
data=p.read_text()
sep='server {\n  listen 172.22.120.240:443 ssl;'
head,mid,tail=data.partition(sep)
if not mid: raise SystemExit('Missing HTTPS block; no changes applied')
old_root='''  location / {
    root /var/www/sibyl-staging;
    index index.html;
    try_files $uri $uri/ =404;
  }
'''
new_root='''  location / {
    return 308 https://$host$request_uri;
  }
'''
old_health='''  location = /health {
    default_type text/plain;
    return 200 "sibyl edge ready\\n";
  }
'''
if old_root in head:
    if head.count(old_root)!=1 or old_health not in head:
        raise SystemExit('Unexpected HTTP site layout')
    head=head.replace(old_root,new_root).replace(old_health,'')
elif new_root not in head:
    raise SystemExit('Unknown HTTP site layout; refusing changes')
p.write_text(head+mid+tail)
PY
nginx -t
systemctl reload nginx
for path in / /index.html /admin.html /addons.html /catalog.json; do
  status="$(curl --noproxy '*' -sS -o /dev/null -w '%{http_code}' --max-time 5 "http://172.22.120.240$path")"
  [[ "$status" == 308 ]] || { echo "Unexpected HTTP status on $path: $status" >&2; exit 1; }
done
echo EDGE_HTTP_ONLY_REDIRECTS_TO_HTTPS
