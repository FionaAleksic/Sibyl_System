#!/usr/bin/env bash
# Sibyl System – standard installation for Ubuntu 24.04 (single host).
# Installs PostgreSQL and Sibyl Core from the pinned public GitHub release.
# Run as root on a fresh, dedicated, trusted VM. Requires HTTPS reverse proxy for clients.
set -euo pipefail
if [[ "${EUID}" -ne 0 ]]; then echo "Run as root" >&2; exit 1; fi
[[ -r /etc/os-release ]] && . /etc/os-release
[[ "${ID:-}" == "ubuntu" && "${VERSION_ID:-}" == "24.04" ]] || {
  echo "This installer supports Ubuntu 24.04 only" >&2; exit 1;
}
VERSION="${SIBYL_VERSION:-0.1.0-rc.2}"
[[ "$VERSION" =~ ^[0-9]+[.][0-9]+[.][0-9]+(-[A-Za-z0-9][A-Za-z0-9.-]*)?$ ]] || exit 1
echo "Installing Sibyl Core $VERSION with local PostgreSQL"
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends postgresql postgresql-client openjdk-21-jre-headless curl openssl ca-certificates
install -d -o root -g root -m 0700 /etc/sibyl
install -d -o root -g root -m 0755 /opt/sibyl
if ! id sibyl >/dev/null 2>&1; then
  useradd --system --home-dir /var/lib/sibyl --shell /usr/sbin/nologin --user-group sibyl
fi
install -d -o sibyl -g sibyl -m 0700 /var/lib/sibyl/addons
secrets=/etc/sibyl/core.env
if [[ -s "$secrets" ]]; then
  # Only root can modify the configuration. Never rotate DB credentials on normal upgrades.
  db_pass="$(sed -n 's/^SIBYL_DB_PASSWORD=//p' "$secrets")"
  [[ "$db_pass" =~ ^[a-f0-9]{64}$ ]] || {
    echo "Unexpected DB secret format: manual review required" >&2; exit 1;
  }
else
  db_pass="$(openssl rand -hex 32)"
fi

# Credentials are streamed on private stdin, never logged or placed into Git.
{
  printf "SELECT format('CREATE ROLE %%I LOGIN', 'sibyl') WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='sibyl') \\gexec\n"
  printf "ALTER ROLE sibyl PASSWORD '%s';\n" "$db_pass"
  printf "SELECT format('CREATE DATABASE %%I OWNER %%I', 'sibyl','sibyl') WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname='sibyl') \\gexec\n"
} | runuser -u postgres -- psql -X -q -v ON_ERROR_STOP=1 -d postgres

{
  echo 'SIBYL_DB_URL=jdbc:postgresql://127.0.0.1:5432/sibyl'
  echo 'SIBYL_DB_USER=sibyl'
  printf 'SIBYL_DB_PASSWORD=%s\n' "$db_pass"
  echo 'SIBYL_LISTEN_ADDRESS=127.0.0.1'
  echo 'SIBYL_PORT=8080'
  echo 'SIBYL_ADDON_DATA=/var/lib/sibyl/addons'
} > "$secrets"
unset db_pass
chmod 0600 "$secrets"
chown root:root "$secrets"

asset="sibyl-core-$VERSION.jar"
url="https://github.com/FionaAleksic/Sibyl_System/releases/download/v$VERSION/$asset"
api="https://api.github.com/repos/FionaAleksic/Sibyl_System/releases/tags/v$VERSION"
tempdir="$(mktemp -d)"; trap 'rm -rf "$tempdir"' EXIT
curl -fLsS --retry 2 --connect-timeout 12 --max-time 45 "$api" >"$tempdir/release.json"
python3 - "$tempdir/release.json" "$asset" >"$tempdir/asset.sha" <<'PY'
import json, sys
v=json.load(open(sys.argv[1]))
assert not v.get('draft'), "Cannot install a draft release"
assert v.get('tag_name','').startswith('v'), "Invalid release tag"
assets=[a for a in v.get('assets',[]) if a.get('name')==sys.argv[2]]
assert len(assets)==1, "Missing exact GitHub release JAR"
digest=assets[0].get('digest','')
assert digest.startswith('sha256:') and len(digest)==71, "Missing GitHub SHA256 digest"
print(digest.removeprefix('sha256:'))
PY
curl -fLsS --retry 2 --connect-timeout 12 --max-time 240 "$url" -o "$tempdir/$asset"
actual="$(sha256sum "$tempdir/$asset" | cut -d' ' -f1)"
expected="$(cat "$tempdir/asset.sha")"
[[ "$actual" == "$expected" ]] || { echo "GitHub release digest mismatch" >&2; exit 1; }
install -o root -g root -m 0644 "$tempdir/$asset" "/opt/sibyl/$asset"
ln -sfn "/opt/sibyl/$asset" /opt/sibyl/current.jar
cat >/etc/systemd/system/sibyl-core.service <<'UNIT'
[Unit]
Description=Sibyl Core (PostgreSQL backed)
After=network-online.target postgresql.service
Requires=postgresql.service
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
UMask=0077
CapabilityBoundingSet=
[Install]
WantedBy=multi-user.target
UNIT
systemctl daemon-reload
systemctl enable --now postgresql
systemctl enable --now sibyl-core
for attempt in {1..30}; do
  if curl -fsS --max-time 3 http://127.0.0.1:8080/actuator/health >/dev/null; then
    echo "Sibyl Core and PostgreSQL ready. Login: admin / friend"
    echo "You MUST change the initial password before using any administrator functions."
    echo "The HTTP endpoint is loopback-only. Configure a trusted HTTPS reverse proxy before browser access."
    exit 0
  fi
  sleep 2
done
journalctl -u sibyl-core -n 30 --no-pager >&2
echo "Sibyl did not start; examine logs" >&2
exit 1
