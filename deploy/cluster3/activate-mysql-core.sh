#!/usr/bin/env bash
# Cluster 3 migration only: existing three-VM Sibyl deployment to MySQL Core.
# Never run on production, the earlier test cluster, or on the DB/Edge VM.
# Password is requested interactively or read from a root-only file already
# placed there by an authorized administrator. No secrets are in this script.
set -euo pipefail
umask 077
[[ "$(id -u)" -eq 0 && "$(hostname)" == sibyl-app-lab ]] || {
  echo "Requires root on sibyl-app-lab only"; exit 1;
}
OLD=/opt/sibyl/sibyl-core-0.1.0-rc.1.jar
VERSION="${SIBYL_CORE_VERSION:-0.1.0-rc.5}"
NEW="/opt/sibyl/sibyl-core-${VERSION}.jar"
CHECKSUM="${SIBYL_CORE_SHA256:-1c516b0f7ee541a0bdccb0e18f8ee72aa4be2fae2b02e4de0f6f1a68fcce01e0}"
[[ "$VERSION" =~ ^[0-9]+[.][0-9]+[.][0-9]+(-[A-Za-z0-9][A-Za-z0-9.-]*)?$ ]] || exit 1
[[ "$CHECKSUM" =~ ^[a-fA-F0-9]{64}$ ]] || exit 1
ENVFILE=/etc/sibyl/core.env
UNIT=/etc/systemd/system/sibyl-core.service
SECRET_FILE=/etc/sibyl/mysql-lab.password
BACKUP_DIR="/etc/sibyl/rollback-before-mysql-$(date -u +%Y%m%dT%H%M%SZ)"

[[ -f "$OLD" && -f "$NEW" && -f "$ENVFILE" && -f "$UNIT" ]] || {
  echo "Missing current version or staged GitHub rc.3 asset"; exit 1;
}
echo "$CHECKSUM  $NEW" | sha256sum --check --status || {
  echo "Invalid MySQL release JAR checksum"; exit 1;
}
command -v mysql >/dev/null || {
  echo "Install mysql-client on this VM first"; exit 1;
}
[[ -f "$SECRET_FILE" && ! -L "$SECRET_FILE" ]] || {
  echo "No machine-provisioned MySQL secret. Refusing interactive fallback." >&2
  exit 1
}
[[ "$(stat -c '%a' "$SECRET_FILE")" == 600 ]] || {
  echo "MySQL secret file must be mode 0600"; exit 1;
}
pass="$(cat "$SECRET_FILE")"
[[ "$pass" =~ ^[a-f0-9]{64}$ ]] || { echo "Wrong secret format"; unset pass; exit 1; }
# Validate MySQL access BEFORE touching the existing Core service.
testres="$(MYSQL_PWD="$pass" mysql --protocol=TCP --ssl-mode=REQUIRED \
  --host=172.22.120.242 --port=3306 --user=sibyl --connect-timeout=8 \
  --batch --skip-column-names --database=sibyl \
  -e "SELECT DATABASE();" 2>/dev/null)" || {
    echo "MySQL login failed; existing Core remains unchanged"; unset pass; exit 1;
  }
[[ "$testres" == sibyl ]] || {
  echo "Unexpected database"; unset pass; exit 1;
}
echo "MySQL encrypted connection and account validated"

install -d -o root -g root -m 0700 "$BACKUP_DIR"
cp -a "$ENVFILE" "$BACKUP_DIR/core.env"
cp -a "$UNIT" "$BACKUP_DIR/sibyl-core.service"
if [[ -L /opt/sibyl/current-cluster.jar ]]; then
  cp -a /opt/sibyl/current-cluster.jar "$BACKUP_DIR/current-cluster.jar"
fi
rollback() {
  local result="$?"
  if [[ "$result" != 0 ]]; then
    echo "Migration failed; restoring previous application service/configuration" >&2
    cp -a "$BACKUP_DIR/core.env" "$ENVFILE"
    cp -a "$BACKUP_DIR/sibyl-core.service" "$UNIT"
    if [[ -L "$BACKUP_DIR/current-cluster.jar" ]]; then
      cp -a "$BACKUP_DIR/current-cluster.jar" /opt/sibyl/current-cluster.jar
    else
      rm -f /opt/sibyl/current-cluster.jar
    fi
    systemctl daemon-reload
    systemctl restart sibyl-core || true
  fi
  unset pass
}
trap rollback EXIT

cat >"$ENVFILE" <<EOF
SIBYL_DB_URL=jdbc:mysql://172.22.120.242:3306/sibyl?sslMode=REQUIRED&connectionTimeZone=UTC
SIBYL_DB_USER=sibyl
SIBYL_DB_PASSWORD=$pass
SIBYL_LISTEN_ADDRESS=172.22.120.241
SIBYL_PORT=8080
SIBYL_ADDON_DATA=/var/lib/sibyl/addons
EOF
chmod 0600 "$ENVFILE"
chown root:root "$ENVFILE"
unset pass

cat >"$UNIT" <<'UNIT'
[Unit]
Description=Sibyl Core (MySQL, managed release)
After=network-online.target
Wants=network-online.target
[Service]
Type=simple
User=sibyl
Group=sibyl
EnvironmentFile=/etc/sibyl/core.env
WorkingDirectory=/var/lib/sibyl
ExecStart=/usr/bin/java -XX:MaxRAMPercentage=65 -jar /opt/sibyl/current-cluster.jar
Restart=on-failure
RestartSec=6
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=true
ReadWritePaths=/var/lib/sibyl
CapabilityBoundingSet=
UMask=0077
LimitNOFILE=8192
[Install]
WantedBy=multi-user.target
UNIT
chmod 0644 "$UNIT"
ln -sfn "$NEW" /opt/sibyl/current-cluster.jar
systemctl daemon-reload
systemctl restart sibyl-core
for attempt in $(seq 1 35); do
  if curl -fsS --max-time 3 http://172.22.120.241:8080/actuator/health >/dev/null &&
     test "$(curl -sS --max-time 3 -o /dev/null -w '%{http_code}' \
         http://172.22.120.241:8080/api/v1/settings/public)" = 401; then
    echo "MYSQL_CORE_RUNNING: v$VERSION"
    echo "MySQL ready; private settings correctly require authentication."
    echo "Previous version rollback files are in $BACKUP_DIR"
    exit 0
  fi
  sleep 2
done
journalctl -u sibyl-core -n 25 --no-pager >&2
exit 1
