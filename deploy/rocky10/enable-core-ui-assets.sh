#!/usr/bin/env bash
# Forward legacy authenticated Sibyl Core JS/CSS so admin and addon browser work behind React V2.
set -Eeuo pipefail
[[ $EUID -eq 0 ]] || { echo 'Run as root' >&2; exit 1; }
conf=/etc/nginx/conf.d/sibyl-v2.conf
backup=/etc/nginx/conf.d/sibyl-v2.conf.pre-core-assets-20261013
[[ -f "$conf" ]] || { echo 'Nginx Sibyl config missing' >&2; exit 1; }
[[ ! -e "$backup" ]] && cp -p "$conf" "$backup"
rollback(){ cp -p "$backup" "$conf"; nginx -t && systemctl reload nginx || true; }
trap rollback ERR
python3 - "$conf" <<'PY'
from pathlib import Path
import sys
p=Path(sys.argv[1]); s=p.read_text()
if 'location ~ ^/(sibyl' not in s:
    anchor='    location ^~ /api/ {'
    assert s.count(anchor)==1, 'Unexpected nginx config; abort'
    snippet = (
       '    # Protected Core assets for Login/Admin/Addon Browser. Backend still checks session.\n'
       '    location ~ ^/(sibyl\\.(css|js)|styles\\.css|addon-browser\\.js|module\\.js)$ {\n'
       '        proxy_pass http://172.22.120.248:8080;\n'
       '        proxy_set_header Host $host;\n'
       '        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;\n'
       '        proxy_set_header X-Forwarded-Proto https;\n'
       '    }\n'
    )
    p.write_text(s.replace(anchor, ''.join(snippet)+anchor, 1))
PY
nginx -t
systemctl reload nginx
base=https://172.22.120.247
# Allow Nginx to finish reloading and check responses. Authenticated assets redirect without session.
for i in $(seq 1 12); do
  code=$(curl -ksS -o /dev/null -w '%{http_code}' --max-time 5 "$base/sibyl.css")
  [[ "$code" == 302 ]] && break
  sleep 1
done
[[ "$code" == 302 ]] || { echo 'Core asset routing failed' >&2; rollback; exit 1; }
[[ $(curl -ksS -o /dev/null -w '%{http_code}' --max-time 6 "$base/login.html") == 200 ]]
[[ $(curl -ksS -o /dev/null -w '%{http_code}' --max-time 6 "$base/") == 200 ]]
echo 'Core UI asset proxy active; unauthenticated /sibyl.css HTTP' "$code"
trap - ERR
