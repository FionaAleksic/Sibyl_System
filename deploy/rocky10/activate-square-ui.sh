#!/usr/bin/env bash
# Activate the verified square Sibyl UI (Rocky VM207), keeping previous release for rollback.
set -Eeuo pipefail
umask 022
test "$EUID" -eq 0 || { echo 'Use sudo bash' >&2; exit 1; }
. /etc/os-release
case "$VERSION_ID" in 10.*) ;; *) echo 'Rocky 10 required' >&2; exit 1;; esac
zip=/home/fiona/sibyl-ui-square-57e33b4273.zip
expected=5103f6f04611998097bf54e68443f639d675e62b5e721e9b6fedadd13fadca4a
base=/srv/sibyl/frontend-v2
release=/srv/sibyl/frontend-v2/releases/57e33b427327a74faee91233c2f1257847f99f03
conf=/etc/nginx/conf.d/sibyl-v2.conf
backup=/etc/nginx/conf.d/sibyl-v2.conf.bak-20261013-design
test -f "$zip" && test -f "$conf" && test -L "$base/current" || { echo 'Missing archive, site or current symlink' >&2; exit 1; }
test "$(sha256sum "$zip" | cut -d' ' -f1)" = "$expected" || { echo 'ZIP digest mismatch' >&2; exit 1; }
previous=$(readlink -f "$base/current")
test -f "$previous/index.html" || { echo 'Previous release missing' >&2; exit 1; }
if test ! -e "$backup"; then cp -p "$conf" "$backup"; fi
finished=0
rollback(){ if test "$finished" -ne 1; then
  ln -sfn "$previous" "$base/current.rollback"; mv -Tf "$base/current.rollback" "$base/current"
  cp -p "$backup" "$conf"; nginx -t && systemctl reload nginx || true
  echo 'Rollback applied after deployment failure' >&2
fi; }
trap rollback EXIT
if test ! -d "$release"; then
  install -d -m 0755 "$release"
  python3 - "$zip" "$release" <<'PY'
import sys, zipfile
from pathlib import PurePosixPath
with zipfile.ZipFile(sys.argv[1]) as z:
    files=z.infolist(); names={p.filename for p in files}
    assert {'index.html','login.css'} <= names
    assert any(x.startswith('assets/') and x.endswith('.js') for x in names)
    assert any(x.startswith('assets/') and x.endswith('.css') for x in names)
    assert all(not x.startswith('/') and '..' not in PurePosixPath(x).parts for x in names)
    assert sum(x.file_size for x in files)<20000000
    z.extractall(sys.argv[2])
PY
  find "$release" -type d -exec chmod 0755 {} +
  find "$release" -type f -exec chmod 0644 {} +
fi
restorecon -RF "$base" || true
python3 - "$conf" <<'PY'
import sys
from pathlib import Path
f=Path(sys.argv[1]); contents=f.read_text()
marker='    location = /healthz {'
snippet='    location = /login.css {\n        try_files /login.css =404;\n    }\n'
if 'location = /login.css {' not in contents:
    assert contents.count(marker)==1, 'Nginx config differs from expected layout'
    f.write_text(contents.replace(marker,snippet+marker,1))
PY
nginx -t
ln -sfn "$release" "$base/current.next"
mv -Tf "$base/current.next" "$base/current"
systemctl reload nginx
url=https://172.22.120.247
test "$(curl -ksS --max-time 10 -o /dev/null -w '%{http_code}' "$url/")" = 200
test "$(curl -ksS --max-time 10 -o /dev/null -w '%{http_code}' "$url/login.html")" = 200
test "$(curl -ksS --max-time 10 -o /dev/null -w '%{http_code}' "$url/login.css")" = 200
login_css=$(curl -ksS --max-time 10 "$url/login.css")
grep -Fq 'border-radius:0' <<< "$login_css"
api=$(curl -ksS --max-time 10 -o /dev/null -w '%{http_code}' "$url/api/v1/modules")
test "$api" = 401 || test "$api" = 200
finished=1
echo "New square frontend active; previous release retained: $previous"
