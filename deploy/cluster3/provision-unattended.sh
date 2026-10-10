#!/usr/bin/env bash
# Headless Sibyl cluster-3 installation controller; intended to be shipped
# in a verified GitHub Core RELEASE, not run from a branch archive.
# Executes only with previously approved pinned SSH host keys and sudo rules.
# If policy denies a secret transfer, ABORT. Never switch to another channel.
set -euo pipefail
set +x
umask 077

VERSION="${SIBYL_CORE_VERSION:-0.1.0-rc.5}"
[[ "$VERSION" =~ ^[0-9]+[.][0-9]+[.][0-9]+(-[A-Za-z0-9][A-Za-z0-9.-]*)?$ ]] ||
  { echo "Invalid Core release tag" >&2; exit 2; }
[[ "${SIBYL_TARGET:-}" == cluster3-lab ]] || {
  echo "Explicit lab target required: SIBYL_TARGET=cluster3-lab" >&2; exit 2;
}
APP_IP=172.22.120.241
DB_IP=172.22.120.242
EDGE_IP=172.22.120.240
MANAGER_USER="${SIBYL_SSH_USER:-fiona}"
KEY="${SIBYL_SSH_KEY:-/home/fiona/.ssh/id_ed25519_sibyl_lab}"
KNOWN_HOSTS="${SIBYL_KNOWN_HOSTS:-/home/fiona/.ssh/known_hosts_sibyl}"
SECRET_FILE="${SIBYL_CLUSTER_SECRET_FILE:-/home/fiona/.sibyl-mysql-deploy-secret-20261009}"
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
for f in bootstrap-mysql-db.sh activate-mysql-core.sh; do
  [[ -s "$HERE/$f" ]] || { echo "Bundle is missing $f" >&2; exit 2; }
  bash -n "$HERE/$f"
done
for command in ssh scp curl python3 sha256sum openssl flock; do
  command -v "$command" >/dev/null || { echo "Missing required command: $command" >&2; exit 2; }
done
[[ -r "$KEY" && -r "$KNOWN_HOSTS" ]] || {
  echo "An authorized SSH identity and pinned known_hosts are required" >&2; exit 2;
}
SSH=(ssh -T -o BatchMode=yes -o ConnectTimeout=9
  -o StrictHostKeyChecking=yes -o UserKnownHostsFile="$KNOWN_HOSTS" -i "$KEY")
SCP=(scp -q -o BatchMode=yes -o ConnectTimeout=9
  -o StrictHostKeyChecking=yes -o UserKnownHostsFile="$KNOWN_HOSTS" -i "$KEY")
app="${MANAGER_USER}@${APP_IP}"
db="${MANAGER_USER}@${DB_IP}"
edge="${MANAGER_USER}@${EDGE_IP}"

# Idempotent lock; do not allow parallel database password changes.
exec 9>"/tmp/sibyl-cluster3-installer.lock"
flock -n 9 || { echo "Another installer instance is running" >&2; exit 2; }
tempdir="$(mktemp -d)"
trap 'rm -rf "$tempdir"' EXIT

echo "[1/8] Preflight host identities and permissions; no changes yet"
[[ "$("${SSH[@]}" "$app" hostname)" == sibyl-app-lab ]] || exit 2
[[ "$("${SSH[@]}" "$db" hostname)" == sibyl-db-lab ]] || exit 2
[[ "$("${SSH[@]}" "$edge" hostname)" == sibyl-edge-lab ]] || exit 2
"${SSH[@]}" "$app" 'sudo -n true; command -v java; command -v mysql; test -f /etc/systemd/system/sibyl-core.service'
"${SSH[@]}" "$db" 'sudo -n true'
"${SSH[@]}" "$edge" 'sudo -n true; systemctl is-active --quiet nginx'

echo "[2/8] Verify GitHub release metadata and unique Core JAR digest"
curl -fLsS --retry 2 --connect-timeout 12 --max-time 45 \
  "https://api.github.com/repos/FionaAleksic/Sibyl_System/releases/tags/v$VERSION" \
  -o "$tempdir/release.json"
digest="$(python3 - "$tempdir/release.json" "$VERSION" <<'PY'
import json,re,sys
release=json.load(open(sys.argv[1],encoding='utf-8'))
version=sys.argv[2]
assert release.get('tag_name')=='v'+version and not release.get('draft')
name='sibyl-core-'+version+'.jar'
assets=[a for a in release.get('assets',[]) if a.get('name')==name]
assert len(assets)==1, 'Missing exact, unique published Core JAR'
digest=assets[0].get('digest','')
assert re.fullmatch(r'sha256:[a-fA-F0-9]{64}',digest), 'GitHub asset digest missing'
print(digest.partition(':')[2].lower())
PY
)"
[[ "$digest" =~ ^[a-f0-9]{64}$ ]] || exit 2
echo "Release metadata and digest verified, v$VERSION"

echo "[3/8] Stage Core JAR directly from GitHub on app; keep current service running"
"${SSH[@]}" "$app" "sudo -n bash -c 'set -euo pipefail
  mkdir -p /opt/sibyl
  target=/opt/sibyl/sibyl-core-${VERSION}.jar
  tmp=\$(mktemp /opt/sibyl/.core-XXXXXXXX)
  trap \"rm -f \\\"\$tmp\\\"\" EXIT
  curl -fLsS --retry 2 --connect-timeout 12 --max-time 240 \
    https://github.com/FionaAleksic/Sibyl_System/releases/download/v${VERSION}/sibyl-core-${VERSION}.jar -o \"\$tmp\"
  echo \"${digest}  \$tmp\" | sha256sum --check --status
  install -o root -g root -m 0644 \"\$tmp\" \"\$target\"
  echo CORE_RELEASE_VERIFIED_AND_STAGED'"

echo "[4/8] Acquire existing machine credential or generate on first install"
if [[ ! -e "$SECRET_FILE" ]]; then
  install -d -m 0700 "$(dirname "$SECRET_FILE")"
  (umask 077; openssl rand -hex 32 > "$SECRET_FILE")
fi
[[ -f "$SECRET_FILE" && ! -L "$SECRET_FILE" &&
  "$(stat -c '%a' "$SECRET_FILE")" == 600 &&
  "$(wc -c <"$SECRET_FILE")" == 65 ]] || {
  echo "Refusing unexpected or unprotected secret file" >&2; exit 2;
}

echo "[5/8] Distribute credential only through APPROVED encrypted SSH transport"
# When policy rejects the transfer, stop. Do not substitute tunnels,
# encoding tricks, alternate tools, or an insecure HTTP endpoint.
"${SSH[@]}" "$app" 'sudo -n install -d -o root -g root -m 0700 /etc/sibyl'
"${SSH[@]}" "$app" 'sudo -n install -o root -g root -m 0600 /dev/stdin /etc/sibyl/mysql-lab.password' \
  <"$SECRET_FILE"

echo "[6/8] Configure DB account and TLS-bound MySQL for exactly this app VM"
"${SCP[@]}" "$HERE/bootstrap-mysql-db.sh" "$db:/home/fiona/bootstrap-sibyl-mysql-db.sh"
"${SSH[@]}" "$db" 'sudo -n bash /home/fiona/bootstrap-sibyl-mysql-db.sh' \
  <"$SECRET_FILE"

echo "[7/8] Validate database and switch Core with automatic rollback"
"${SCP[@]}" "$HERE/activate-mysql-core.sh" "$app:/home/fiona/activate-sibyl-mysql-core.sh"
"${SSH[@]}" "$app" "sudo -n env SIBYL_CORE_VERSION='$VERSION' SIBYL_CORE_SHA256='$digest' \
  bash /home/fiona/activate-sibyl-mysql-core.sh"

echo "[8/8] Verify anonymous sees ONLY login; private pages and APIs are gated"
curl -k -fsS --max-time 15 "https://$EDGE_IP/actuator/health" -o "$tempdir/health"
test "$(curl -k -sS --max-time 15 -o /dev/null -w '%{http_code}' \
    "https://$EDGE_IP/login.html")" = 200
for page in / /admin.html /addons.html /index.html; do
  result="$(curl -k -sS --max-time 15 -o /dev/null -w '%{http_code}' \
      "https://$EDGE_IP$page")"
  [[ "$result" == 302 ]] || { echo "Protected page $page returned $result, expected 302" >&2; exit 1; }
done
for api in /api/v1/auth/me /api/v1/settings/public /api/v1/addons/catalog; do
  result="$(curl -k -sS --max-time 15 -o /dev/null -w '%{http_code}' \
      "https://$EDGE_IP$api")"
  [[ "$result" == 401 ]] || { echo "Protected API $api returned $result, expected 401" >&2; exit 1; }
done
echo "CLUSTER3_SIBYL_CORE_DEPLOYMENT_SUCCEEDED"
echo "Anonymous pages and APIs protected; existing MySQL accounts were preserved."
