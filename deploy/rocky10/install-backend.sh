#!/usr/bin/env bash
# Sibyl Rocky Linux 10 lab backend bootstrap. Only published, checksum-verified Core releases.
# Run on a NEW, isolated lab VM; do not execute on an existing production database.
set -Eeuo pipefail
umask 077
[[ ${EUID} -eq 0 ]] || { echo "Run as root" >&2; exit 1; }
source /etc/os-release
[[ "${ID}" == rocky && "${VERSION_ID%%.*}" == 10 ]] || { echo "Rocky Linux 10 required" >&2; exit 1; }
version="${SIBYL_VERSION:-0.1.0-rc.16}"
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9][A-Za-z0-9.-]*)?$ ]] || { echo "Invalid version" >&2; exit 1; }
listen="${SIBYL_LISTEN_ADDRESS:-127.0.0.1}"
[[ "$listen" == 127.0.0.1 || "$listen" =~ ^172\.22\.120\.[0-9]{1,3}$ ]] || { echo "Listen address outside authorized lab" >&2; exit 1; }
frontend="${SIBYL_FRONTEND_IP:-172.22.120.247}"
[[ "$frontend" =~ ^172\.22\.120\.[0-9]{1,3}$ ]] || { echo "Invalid frontend lab IP" >&2; exit 1; }

# Distinguish a truly new RPM MySQL installation from an existing database.
fresh_mysql=0
[[ -f /var/lib/mysql/auto.cnf ]] || fresh_mysql=1
# Minimal Rocky cloud images may contain a stale python-unversioned-command RPM.
# If an RPM mirror version mismatch occurs, resolve it before retrying this installer.
dnf -y install mysql8.4-server mysql8.4 java-21-openjdk-headless curl ca-certificates openssl python3 firewalld policycoreutils
# MySQL is needed only by the local Java backend; block listening on the VM network.
cat >/etc/my.cnf.d/sibyl-loopback.cnf <<'MYSQL_CFG'
[mysqld]
bind-address=127.0.0.1
mysqlx-bind-address=127.0.0.1
MYSQL_CFG
chmod 0644 /etc/my.cnf.d/sibyl-loopback.cnf
mysqld --validate-config
systemctl enable --now mysqld firewalld
install -d -m 0700 /etc/sibyl
root_opts=/etc/sibyl/mysql-root.cnf
mysqlcmd=(mysql --protocol=socket --user=root --batch --silent)
if [[ -f "$root_opts" ]]; then
    mysqlcmd=(mysql --defaults-extra-file="$root_opts" --protocol=socket --user=root --batch --silent)
fi
if ! "${mysqlcmd[@]}" -e "SELECT VERSION()" >/dev/null 2>&1; then
    # MySQL RPM may generate a one-time, expired random password; use it only on
    # a FRESH instance and rotate immediately. Never reset an existing database.
    [[ "$fresh_mysql" == 1 && ! -e "$root_opts" ]] || {
        echo "Existing MySQL root authentication is unavailable; refusing credential reset" >&2
        exit 1
    }
    temporary="$(sed -n 's/^.*temporary password is generated for root@localhost: //p' /var/log/mysqld.log 2>/dev/null | tail -1)"
    [[ -n "$temporary" ]] || {
        echo "No fresh-instance MySQL temporary root password in /var/log/mysqld.log" >&2
        exit 1
    }
    rootpw="$(openssl rand -hex 32)"
    MYSQL_PWD="$temporary" mysql --protocol=socket --user=root --connect-expired-password --batch --silent \
      -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootpw';"
    unset temporary
    printf '[client]\nuser=root\npassword=%s\n' "$rootpw" > "$root_opts"
    chmod 0600 "$root_opts"
    unset rootpw
    mysqlcmd=(mysql --defaults-extra-file="$root_opts" --protocol=socket --user=root --batch --silent)
fi
"${mysqlcmd[@]}" -e "SELECT VERSION()" >/dev/null
# Secure a genuinely fresh no-password root account, if a vendor image exposes one.
if [[ "$fresh_mysql" == 1 && ! -e "$root_opts" ]]; then
    rootpw="$(openssl rand -hex 32)"
    "${mysqlcmd[@]}" -e "ALTER USER 'root'@'localhost' IDENTIFIED BY '$rootpw';"
    printf '[client]\nuser=root\npassword=%s\n' "$rootpw" > "$root_opts"
    chmod 0600 "$root_opts"
    unset rootpw
    mysqlcmd=(mysql --defaults-extra-file="$root_opts" --protocol=socket --user=root --batch --silent)
fi
install -d -m 0755 /opt/sibyl
id sibyl &>/dev/null || useradd --system --home-dir /var/lib/sibyl --shell /sbin/nologin --user-group sibyl
install -d -o sibyl -g sibyl -m 0700 /var/lib/sibyl /var/lib/sibyl/addons

envfile=/etc/sibyl/core.env
if [[ -e "$envfile" ]]; then
  dbpass="$(sed -n 's/^SIBYL_DB_PASSWORD=//p' "$envfile")"
  [[ "$dbpass" =~ ^[a-f0-9]{64}$ ]] || { echo "Refusing to overwrite unexpected database credentials" >&2; exit 1; }
else
  dbpass="$(openssl rand -hex 32)"
fi
{
  printf 'CREATE DATABASE IF NOT EXISTS sibyl CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\n'
  for host in localhost 127.0.0.1; do
    printf "CREATE USER IF NOT EXISTS 'sibyl'@'%s' IDENTIFIED BY '%s';\n" "$host" "$dbpass"
    printf "ALTER USER 'sibyl'@'%s' IDENTIFIED BY '%s';\n" "$host" "$dbpass"
    printf "GRANT ALL PRIVILEGES ON sibyl.* TO 'sibyl'@'%s';\n" "$host"
  done
} | "${mysqlcmd[@]}"
{
  printf 'SIBYL_DB_URL=jdbc:mysql://127.0.0.1:3306/sibyl?sslMode=REQUIRED&connectionTimeZone=UTC\n'
  printf 'SIBYL_DB_USER=sibyl\nSIBYL_DB_PASSWORD=%s\n' "$dbpass"
  printf 'SIBYL_LISTEN_ADDRESS=%s\nSIBYL_PORT=8080\nSIBYL_ADDON_DATA=/var/lib/sibyl/addons\n' "$listen"
} > "$envfile"
chown root:root "$envfile"
chmod 0600 "$envfile"
unset dbpass

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
asset="sibyl-core-$version.jar"
curl -fLSs --retry 2 --connect-timeout 12 --max-time 45 "https://api.github.com/repos/FionaAleksic/Sibyl_System/releases/tags/v$version" -o "$tmp/release.json"
python3 - "$tmp/release.json" "$version" "$asset" >"$tmp/digest" <<'PY'
import json, re, sys
release = json.load(open(sys.argv[1], encoding="utf-8"))
if release.get("draft") or release.get("tag_name") != "v" + sys.argv[2]:
    raise SystemExit("Unexpected or draft release")
assets = [x for x in release.get("assets", []) if x.get("name") == sys.argv[3]]
if len(assets) != 1 or not re.fullmatch(r"sha256:[a-fA-F0-9]{64}", assets[0].get("digest","")):
    raise SystemExit("Missing, duplicate or unverified JAR asset")
print(assets[0]["digest"].split(":", 1)[1].lower())
PY
curl -fLSs --retry 2 --connect-timeout 12 --max-time 240 "https://github.com/FionaAleksic/Sibyl_System/releases/download/v$version/$asset" -o "$tmp/$asset"
actual="$(sha256sum "$tmp/$asset" | cut -d' ' -f1)"
[[ "$actual" == "$(cat "$tmp/digest")" ]] || { echo "Release SHA256 mismatch" >&2; exit 1; }
install -o root -g root -m 0644 "$tmp/$asset" "/opt/sibyl/$asset"
ln -sfn "/opt/sibyl/$asset" /opt/sibyl/current.jar

cat >/etc/systemd/system/sibyl-core.service <<'UNIT'
[Unit]
Description=Sibyl System Core (Rocky Linux 10 / MySQL 8.4)
After=network-online.target mysqld.service
Wants=network-online.target
Requires=mysqld.service
[Service]
User=sibyl
Group=sibyl
EnvironmentFile=/etc/sibyl/core.env
WorkingDirectory=/var/lib/sibyl
ExecStart=/usr/bin/java -jar /opt/sibyl/current.jar
Restart=on-failure
RestartSec=5
NoNewPrivileges=yes
PrivateTmp=yes
ProtectSystem=strict
ProtectHome=yes
ReadWritePaths=/var/lib/sibyl
CapabilityBoundingSet=
UMask=0077
[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable --now sibyl-core
systemctl restart sibyl-core
for i in $(seq 1 40); do
  if curl -fsS --max-time 3 "http://$listen:8080/actuator/health" >/dev/null; then
    echo "Sibyl backend localhost health OK"
    break
  fi
  if (( i == 40 )); then
    echo "Health check failed; inspect systemctl/journalctl and SELinux AVCs" >&2
    exit 1
  fi
  sleep 2
done
if [[ "$listen" != 127.0.0.1 ]]; then
  firewall-cmd --permanent --remove-service=cockpit || true
  firewall-cmd --permanent --add-rich-rule="rule family=ipv4 source address=$frontend/32 port port=8080 protocol=tcp accept"
  firewall-cmd --reload
  echo "Port 8080 allowed in firewalld to configured frontend $frontend only; audit other zones and external firewall rules."
fi
echo "Sibyl backend installed. Change bootstrap admin password before expanding frontend access."
