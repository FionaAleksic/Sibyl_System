#!/usr/bin/env bash
# Add the generic authenticated extension asset route on existing Rocky frontend.
set -Eeuo pipefail
[[ $EUID -eq 0 ]] || exit 1
config=/etc/nginx/conf.d/sibyl-v2.conf
backup=/etc/nginx/conf.d/sibyl-v2.conf.before-addon-ui-route-20261013
[[ -f "$config" ]] || exit 1
if ! grep -Fq 'location ^~ /extensions/' "$config"; then
  cp -p "$config" "$backup"
  python3 - "$config" <<'PY'
import sys
from pathlib import Path
p=Path(sys.argv[1]); text=p.read_text()
anchor='    location ^~ /api/ {'
assert text.count(anchor)==1,'Unexpected nginx site'
route='''    # Authenticated, sandboxed add-on UI assets from the Sibyl Core.
    location ^~ /extensions/ {
        proxy_pass http://172.22.120.248:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_connect_timeout 3s;
        proxy_read_timeout 30s;
    }
'''
p.write_text(text.replace(anchor,route+anchor,1))
PY
  if ! nginx -t; then cp -p "$backup" "$config"; exit 1; fi
  systemctl reload nginx
fi
# With no admin cookie this must be a backend authorization redirect, not React HTML.
code=0
for n in $(seq 1 12); do
  code=$(curl -ksS --max-time 5 -o /dev/null -w '%{http_code}' \
     https://172.22.120.247/extensions/Sibyl.doc/ui/index.html)
  [[ $code == 302 ]] && break
  sleep 1
done
[[ $code == 302 ]] || { echo "Extension UI protection failed: HTTP $code" >&2; exit 1; }
echo "Generic authenticated extension UI proxy enabled"
