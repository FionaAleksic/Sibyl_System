#!/usr/bin/env bash
# Safe, repeatable deployment of a checksum-pinned Vite frontend release.
set -Eeuo pipefail
[[ $EUID -eq 0 ]] || { echo 'Run with sudo' >&2; exit 1; }
[[ -n ${FRONTEND_ZIP:-} && -n ${FRONTEND_SHA256:-} && -n ${FRONTEND_COMMIT:-} ]] || { echo 'FRONTEND_ZIP, FRONTEND_SHA256 and FRONTEND_COMMIT required' >&2; exit 1; }
[[ $FRONTEND_SHA256 =~ ^[a-f0-9]{64}$ && $FRONTEND_COMMIT =~ ^[a-f0-9]{40}$ ]] || { echo 'Invalid digest or commit' >&2; exit 1; }
[[ -f $FRONTEND_ZIP ]] || { echo 'ZIP missing' >&2; exit 1; }
[[ $(sha256sum "$FRONTEND_ZIP" | cut -d' ' -f1) == "$FRONTEND_SHA256" ]] || { echo 'SHA256 mismatch' >&2; exit 1; }
base=/srv/sibyl/frontend-v2
release="$base/releases/$FRONTEND_COMMIT"
[[ -L $base/current ]] || { echo 'Existing release symlink missing' >&2; exit 1; }
previous=$(readlink -f "$base/current")
[[ -f $previous/index.html ]] || { echo 'Existing release invalid' >&2; exit 1; }
complete=0
rollback() {
  if [[ $complete != 1 ]]; then
    echo 'Frontend deployment failed; activating previous version' >&2
    ln -sfn "$previous" "$base/current.rollback"
    mv -Tf "$base/current.rollback" "$base/current"
    systemctl reload nginx || true
  fi
}
trap rollback EXIT
if [[ ! -d "$release" ]]; then
  mkdir -m 0755 -p "$release"
  python3 - "$FRONTEND_ZIP" "$release" <<'PY'
from pathlib import Path, PurePosixPath
import sys, zipfile
archive, target = sys.argv[1:]
with zipfile.ZipFile(archive) as z:
    info = z.infolist()
    names = {i.filename for i in info}
    assert {'index.html','login.css'} <= names
    assert any(x.startswith('assets/') and x.endswith('.js') for x in names)
    assert any(x.startswith('assets/') and x.endswith('.css') for x in names)
    assert len(info) <= 100 and sum(x.file_size for x in info) < 25000000
    assert all(not x.startswith('/') and '..' not in PurePosixPath(x).parts for x in names)
    assert all((i.external_attr >> 16) & 0o170000 != 0o120000 for i in info)
    z.extractall(target)
PY
  find "$release" -type d -exec chmod 0755 {} +
  find "$release" -type f -exec chmod 0644 {} +
fi
restorecon -RF "$base" || true
nginx -t
ln -sfn "$release" "$base/current.next"
mv -Tf "$base/current.next" "$base/current"
systemctl reload nginx
site=https://172.22.120.247
for i in $(seq 1 10); do
  if curl -ksS --max-time 5 "$site/" | grep -F 'index-B8fql1t5.js' > /dev/null; then break; fi
  sleep 1
done
curl -ksS --max-time 5 "$site/" | grep -F 'index-B8fql1t5.js' >/dev/null
for path in / /login.html /login.css /healthz; do
  code=$(curl -ksS -o /dev/null -w '%{http_code}' --max-time 6 "$site$path")
  [[ "$code" == 200 ]] || { echo "$path HTTP $code" >&2; exit 1; }
done
code=$(curl -ksS -o /dev/null -w '%{http_code}' --max-time 6 "$site/api/v1/modules")
[[ "$code" == 401 || "$code" == 200 ]] || { echo "API HTTP $code" >&2; exit 1; }
complete=1
trap - EXIT
echo "Frontend active: $FRONTEND_COMMIT ; previous: $previous"
