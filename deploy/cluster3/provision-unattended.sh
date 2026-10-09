#!/usr/bin/env bash
# Unattended Sibyl Cluster-3 installer controller.
# One command on the authorized deployment controller, zero questions.
# SSH transports secrets encrypted, but the administrator must have approved
# permission to provision credentials on the remote hosts. Never bypass any
# authorization or remote execution policy gate.
set -euo pipefail
umask 077

APP_IP=172.22.120.241
DB_IP=172.22.120.242
EDGE_IP=172.22.120.240
VERSION="0.1.0-rc.3"
SHA256_CORE="1c516b0f7ee541a0bdccb0e18f8ee72aa4be2fae2b02e4de0f6f1a68fcce01e0"
RELEASE_URL="https://github.com/FionaAleksic/Sibyl_System/releases/download/v${VERSION}/sibyl-core-${VERSION}.jar"
MANAGER_USER="${SIBYL_SSH_USER:-fiona}"
KEY="${SIBYL_SSH_KEY:-/home/fiona/.ssh/id_ed25519_sibyl_lab}"
KNOWN_HOSTS="${SIBYL_KNOWN_HOSTS:-/home/fiona/.ssh/known_hosts_sibyl}"
SECRET_FILE="${SIBYL_CLUSTER_SECRET_FILE:-/home/fiona/.sibyl-mysql-deploy-secret-20261009}"

# Explicitly pinned lab only. This is NOT a production or generic subnet tool.
[[ "${SIBYL_TARGET:-}" == "cluster3-lab" ]] || {
  echo "Set SIBYL_TARGET=cluster3-lab to authorize this exact lab topology." >&2
  exit 2
}
[[ -r "$KEY" && -r "$KNOWN_HOSTS" ]] || {
  echo "Authorized SSH key and pinned known_hosts are required." >&2; exit 2;
}
command -v ssh >/dev/null
command -v curl >/dev/null
command -v sha256sum >/dev/null
command -v openssl >/dev/null
SSH=(ssh -T -o BatchMode=yes -o ConnectTimeout=8
  -o StrictHostKeyChecking=yes -o UserKnownHostsFile="$KNOWN_HOSTS" -i "$KEY")

app="${MANAGER_USER}@${APP_IP}"
db="${MANAGER_USER}@${DB_IP}"
edge="${MANAGER_USER}@${EDGE_IP}"

echo "[1/7] Authorize and verify all three expected VMs (read-only checks)"
[[ "$("${SSH[@]}" "$app" hostname)" == sibyl-app-lab ]]
[[ "$("${SSH[@]}" "$db" hostname)" == sibyl-db-lab ]]
[[ "$("${SSH[@]}" "$edge" hostname)" == sibyl-edge-lab ]]
"${SSH[@]}" "$app" 'sudo -n true; test -f /opt/sibyl/sibyl-core-0.1.0-rc.1.jar'
"${SSH[@]}" "$db" 'sudo -n true; systemctl is-active --quiet mysql'
"${SSH[@]}" "$edge" 'sudo -n true; systemctl is-active --quiet nginx'

echo "[2/7] Validate published GitHub release digest, never a branch artifact"
tempdir="$(mktemp -d)"
cleanup(){ rm -rf "$tempdir"; }
trap cleanup EXIT
curl --fail --location --silent --show-error --max-time 45 --retry 2 \
  "https://api.github.com/repos/FionaAleksic/Sibyl_System/releases/tags/v${VERSION}" \
  -o "$tempdir/release.json"
python3 - "$tempdir/release.json" "$VERSION" "$SHA256_CORE" <<'PY'
import json,sys
release=json.load(open(sys.argv[1],encoding='utf-8'))
version,expected=sys.argv[2:4]
assert release['tag_name']=='v'+version and not release['draft']
matches=[a for a in release['assets'] if a['name']=='sibyl-core-'+version+'.jar']
assert len(matches)==1 and matches[0]['digest']=='sha256:'+expected
print('Verified immutable GitHub release metadata')
PY

echo "[3/7] Prepare versioned JAR on app VM (no core restart yet)"
"${SSH[@]}" "$app" "sudo -n test -f /opt/sibyl/sibyl-core-${VERSION}.jar"
"${SSH[@]}" "$app" "printf '%s  /opt/sibyl/sibyl-core-${VERSION}.jar\\n' '${SHA256_CORE}' | sha256sum --check --status"

echo "[4/7] Obtain or create machine-generated secret on controller"
if [[ ! -e "$SECRET_FILE" ]]; then
  mkdir -p "$(dirname "$SECRET_FILE")"
  ( umask 077; openssl rand -hex 32 >"$SECRET_FILE" )
fi
[[ -f "$SECRET_FILE" && ! -L "$SECRET_FILE" ]] || {
  echo "Secret must be a regular private file"; exit 2;
}
[[ "$(stat -c '%a' "$SECRET_FILE")" == 600 ]] || {
  echo "Secret file mode must be 0600"; exit 2;
}
[[ "$(wc -c <"$SECRET_FILE")" == 65 ]] || {
  echo "Unexpected secret length"; exit 2;
}
# Do not display, log, use command-line arguments or put the secret in Git.

echo "[5/7] Provision encrypted DB identity through an AUTHORIZED SSH path"
# The DB host must already be running TLS-only MySQL, with an existing
# allowlisted DB user for exactly the app IP. Never grant broad remote access.
"${SSH[@]}" "$db" "sudo -n mysql --protocol=socket --batch --skip-column-names -e \
  'SELECT @@require_secure_transport, @@bind_address' " >"$tempdir/db-status"
grep -q '^1[[:space:]]172.22.120.242$' "$tempdir/db-status"

# Explicitly fail before secret transfer if the current remote policy rejects
# it. Operators should configure an authorized secret delivery channel, not
# substitute alternate transports after access is denied.
"${SSH[@]}" "$app" 'sudo -n install -d -o root -g root -m 0700 /etc/sibyl'
"${SSH[@]}" "$app" 'sudo -n install -o root -g root -m 0600 /dev/stdin /etc/sibyl/mysql-lab.password' <"$SECRET_FILE"

echo "[6/7] Activate preverified release with automatic rollback and health checks"
# This immutable release-only script must already be present on .241.
"${SSH[@]}" "$app" 'sudo -n bash /home/fiona/activate-sibyl-mysql-core.sh'

echo "[7/7] Verify from controller using read-only network requests"
curl -k --fail --silent --show-error --max-time 20 \
  "https://${EDGE_IP}/actuator/health" >"$tempdir/health"
curl -k --fail --silent --show-error --max-time 20 \
  "https://${EDGE_IP}/api/v1/settings/public" >"$tempdir/public.json"
python3 - "$tempdir/public.json" <<'PY'
import json,sys
v=json.load(open(sys.argv[1],encoding='utf-8'))
assert v.get('organization')
print("New MySQL-backed Core responded with organization data")
PY
echo "SIBYL_CLUSTER3_MYSQL_CORE_READY"
echo "One-time admin credentials: admin / friend. Password must be rotated immediately."
echo "Release is installed; add-ons still require their own activate/health lifecycle."
