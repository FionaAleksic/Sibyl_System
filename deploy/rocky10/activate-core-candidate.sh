#!/usr/bin/env bash
# Atomic Core candidate deploy for the existing Rocky backend; never touches the DB.
# Refuses new/altered Flyway migrations until an external backup exists.
set -Eeuo pipefail
umask 077
[[ $EUID -eq 0 ]] || { echo "Run via sudo" >&2; exit 1; }
version=0.1.0-rc.17-dev.1
commit=20976d0e08d5c9bc4cbabdd1b868bbe08cb2c3c1
asset="sibyl-core-$version.jar"
api="https://api.github.com/repos/FionaAleksic/Sibyl_System/releases/tags/v$version"
url="https://github.com/FionaAleksic/Sibyl_System/releases/download/v$version/$asset"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
[[ -L /opt/sibyl/current.jar ]] || { echo "Unexpected Core release layout" >&2; exit 1; }
old=$(readlink -f /opt/sibyl/current.jar)
[[ -f $old ]] || { echo "No previous Core JAR to roll back" >&2; exit 1; }
curl -fsSL --retry 2 --connect-timeout 10 --max-time 30 "$api" -o "$tmp/release.json"
python3 - "$tmp/release.json" "$asset" "$commit" > "$tmp/expected.sha" <<'PY'
import json,re,sys
release=json.load(open(sys.argv[1]))
assert release.get('tag_name')=='v0.1.0-rc.17-dev.1'
assert release.get('prerelease') is True and not release.get('draft')
assert release.get('target_commitish')==sys.argv[3]
assets=[a for a in release.get('assets',[]) if a.get('name')==sys.argv[2]]
assert len(assets)==1
digest=assets[0].get('digest','')
assert re.fullmatch(r'sha256:[a-fA-F0-9]{64}',digest)
print(digest.partition(':')[2].lower())
PY
curl -fsSL --retry 2 --connect-timeout 12 --max-time 240 "$url" -o "$tmp/$asset"
expected=$(cat "$tmp/expected.sha")
actual=$(sha256sum "$tmp/$asset" | cut -d' ' -f1)
[[ "$expected" == "$actual" ]] || { echo "Release JAR checksum mismatch" >&2; exit 1; }
# Prevent unintended automatic Flyway changes on a database with no verified backup.
python3 - "$old" "$tmp/$asset" <<'PY'
import sys,zipfile
def migrations(jar):
  with zipfile.ZipFile(jar) as z:
    return {k:z.read(k) for k in z.namelist() if k.startswith('BOOT-INF/classes/db/migration/')}
assert migrations(sys.argv[1])==migrations(sys.argv[2]), 'Flyway changes require separate backup and approval'
with zipfile.ZipFile(sys.argv[2]) as z:
  names=set(z.namelist())
  for required in ('BOOT-INF/classes/org/sibyl/core/AddonConfigTemplates.class',
    'BOOT-INF/classes/static/addons.js','BOOT-INF/classes/static/admin.js'):
    assert required in names,required
PY
destination="/opt/sibyl/$asset"
install -o root -g root -m 0644 "$tmp/$asset" "$destination"
complete=0
rollback() {
  if [[ "$complete" -ne 1 ]]; then
    echo "Core deployment failed; restoring original JAR" >&2
    ln -sfn "$old" /opt/sibyl/current.jar.previous
    mv -Tf /opt/sibyl/current.jar.previous /opt/sibyl/current.jar
    systemctl restart sibyl-core || true
  fi
}
trap rollback EXIT
ln -sfn "$destination" /opt/sibyl/current.jar.next
mv -Tf /opt/sibyl/current.jar.next /opt/sibyl/current.jar
systemctl restart sibyl-core
for n in $(seq 1 50); do
  if curl -fsS --connect-timeout 2 --max-time 3 http://172.22.120.248:8080/actuator/health > "$tmp/health"; then
    if grep -Fq '"UP"' "$tmp/health"; then break; fi
  fi
  sleep 2
done
grep -Fq '"UP"' "$tmp/health"
complete=1
trap - EXIT
echo "Sibyl Core candidate active: v$version"
echo "Previous JAR preserved: $old"
