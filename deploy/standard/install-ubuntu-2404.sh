#!/usr/bin/env bash
# Sibyl System – single-host standard installation on a NEW Ubuntu 24.04 VM.
# MySQL 8 database is installed automatically, no Active Directory required.
# Install only tested, published GitHub release JARs (never branch source).
set -euo pipefail
umask 077
[[ "$EUID" -eq 0 ]] || { echo "Run as root" >&2; exit 1; }
. /etc/os-release
[[ "${ID:-}" == ubuntu && "${VERSION_ID:-}" == 24.04 ]] || {
  echo "Requires Ubuntu 24.04 LTS" >&2; exit 1;
}
VERSION="${SIBYL_VERSION:-0.1.0-rc.3}"
[[ "$VERSION" =~ ^[0-9]+[.][0-9]+[.][0-9]+(-[A-Za-z0-9][A-Za-z0-9.-]*)?$ ]] ||
  { echo "Invalid release version" >&2; exit 1; }
echo "Installing Sibyl Core $VERSION and local MySQL 8"

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends mysql-server mysql-client openjdk-21-jre-headless ca-certificates curl openssl python3
systemctl enable --now mysql
install -d -o root -g root -m 0700 /etc/sibyl
install -d -o root -g root -m 0755 /opt/sibyl
if ! id sibyl >/dev/null 2>&1; then
  useradd --system --home-dir /var/lib/sibyl --shell /usr/sbin/nologin --user-group sibyl
fi
install -d -o sibyl -g sibyl -m 0700 /var/lib/sibyl
install -d -o sibyl -g sibyl -m 0700 /var/lib/sibyl/addons

# Preserve DB credentials on upgrades. The new account is NEVER seeded again
# after any user exists. Never put database credentials into the browser.
envfile=/etc/sibyl/core.env
if [[ -f "$envfile" ]]; then
  dbpass="$(sed -n 's/^SIBYL_DB_PASSWORD=//p' "$envfile")"
  [[ "$dbpass" =~ ^[a-f0-9]{64}$ ]] || { echo "Review existing DB configuration before upgrade" >&2; exit 1; }
else
  dbpass="$(openssl rand -hex 32)"
fi

# Root can use the Ubuntu MySQL Unix socket; SQL is passed privately via stdin.
# Only hex-generated credentials are interpolated, never user-provided SQL.
{
  printf "CREATE DATABASE IF NOT EXISTS sibyl CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\n"
  for host in localhost 127.0.0.1; do
    printf "CREATE USER IF NOT EXISTS 'sibyl'@'%s' IDENTIFIED BY '%s';\n" "$host" "$dbpass"
    printf "ALTER USER 'sibyl'@'%s' IDENTIFIED BY '%s';\n" "$host" "$dbpass"
    printf "GRANT ALL PRIVILEGES ON sibyl.* TO 'sibyl'@'%s';\n" "$host"
  done
} | mysql --protocol=socket --user=root --batch --silent
cat >"$envfile" <<EOF
SIBYL_DB_URL=jdbc:mysql://127.0.0.1:3306/sibyl?sslMode=REQUIRED&connectionTimeZone=UTC
SIBYL_DB_USER=sibyl
SIBYL_DB_PASSWORD=$dbpass
SIBYL_LISTEN_ADDRESS=127.0.0.1
SIBYL_PORT=8080
SIBYL_ADDON_DATA=/var/lib/sibyl/addons
EOF
unset dbpass
chmod 0600 "$envfile"
chown root:root "$envfile"

# Release metadata and JAR must agree. GitHub digest is verified BEFORE install.
asset="sibyl-core-$VERSION.jar"
api="https://api.github.com/repos/FionaAleksic/Sibyl_System/releases/tags/v$VERSION"
url="https://github.com/FionaAleksic/Sibyl_System/releases/download/v$VERSION/$asset"
temp="$(mktemp -d)"; trap 'rm -rf "$temp"' EXIT
curl -fLsS --retry 2 --connect-timeout 12 --max-time 45 "$api" -o "$temp/release.json"
python3 - "$temp/release.json" "$asset" "$VERSION" >"$temp/sha256" <<'PY'
import json,re,sys
release=json.load(open(sys.argv[1],encoding="utf-8"))
assert not release.get("draft"), "Unpublished draft rejected"
assert release.get("tag_name")=="v"+sys.argv[3], "Wrong release version"
matches=[a for a in release.get("assets",[]) if a.get("name")==sys.argv[2]]
assert len(matches)==1, "Expected exactly one versioned Core JAR asset"
digest=matches[0].get("digest","")
assert re.fullmatch("sha256:[a-fA-F0-9]{64}",digest), "GitHub digest missing"
print(digest.removeprefix("sha256:"))
PY
curl -fLsS --retry 2 --connect-timeout 12 --max-time 240 "$url" -o "$temp/$asset"
[[ "$(sha256sum "$temp/$asset" | cut -d' ' -f1)" == "$(cat "$temp/sha256")" ]] || {
  echo "Core GitHub SHA-256 verification failed" >&2; exit 1;
}
install -o root -g root -m 0644 "$temp/$asset" "/opt/sibyl/$asset"
ln -sfn "/opt/sibyl/$asset" /opt/sibyl/current.jar

cat >/etc/systemd/system/sibyl-core.service <<'UNIT'
[Unit]
Description=Sibyl System Core (MySQL)
After=network-online.target mysql.service
Wants=network-online.target
Requires=mysql.service
[Service]
Type=simple
User=sibyl
Group=sibyl
EnvironmentFile=/etc/sibyl/core.env
WorkingDirectory=/var/lib/sibyl
ExecStart=/usr/bin/java -jar /opt/sibyl/current.jar
Restart=on-failure
RestartSec=5
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/sibyl
CapabilityBoundingSet=
UMask=0077
[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable --now sibyl-core
systemctl restart sibyl-core
for i in $(seq 1 30); do
  if curl -fsS --max-time 3 http://127.0.0.1:8080/actuator/health >/dev/null; then
    echo "Sibyl Core with MySQL ready on loopback: 127.0.0.1:8080"
    echo "Initial admin: admin / friend. A password change is required at first login."
    echo "Configure a trusted HTTPS reverse proxy BEFORE exposing the admin UI."
    exit 0
  fi
  sleep 2
done
echo "Core failed health check; inspect systemctl status sibyl-core and journalctl" >&2
exit 1
