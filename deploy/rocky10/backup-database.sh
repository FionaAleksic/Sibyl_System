#!/usr/bin/env bash
# Secure standalone MySQL backup for Sibyl Rocky 10. Requires an already mounted off-VM share.
# NOT automatically installed or scheduled: approve storage, encryption and restore-check first.
set -Eeuo pipefail
umask 077
[[ $EUID -eq 0 ]] || { echo 'Run as root' >&2; exit 1; }
[[ -n ${BACKUP_DIR:-} ]] || { echo 'BACKUP_DIR missing: require remote mounted backup directory' >&2; exit 1; }
[[ -f /etc/sibyl/mysql-root.cnf ]] || { echo 'MySQL root credential file missing' >&2; exit 1; }
[[ -d $BACKUP_DIR ]] || { echo 'Backup mount path missing' >&2; exit 1; }
target=$(realpath "$BACKUP_DIR")
[[ $target != / && $target != /var && $target != /var/lib ]] || { echo 'Unsafe backup directory' >&2; exit 1; }
mount=$(findmnt -n --target "$target" -o TARGET)
[[ $mount == "$target" ]] || { echo 'BACKUP_DIR must itself be a mounted remote share (no local fallback)' >&2; exit 1; }
fstype=$(findmnt -n --target "$target" -o FSTYPE)
case "$fstype" in nfs|nfs4|cifs|fuse.sshfs) ;; *) echo "Unapproved backup filesystem: $fstype" >&2; exit 1 ;; esac
[[ -w $target ]] || { echo 'Backup share not writable' >&2; exit 1; }
command -v mysqldump >/dev/null && command -v gzip >/dev/null && command -v sha256sum >/dev/null || exit 1
[[ ${RETENTION_DAYS:-30} =~ ^[0-9]{1,3}$ ]] || { echo 'Invalid retention' >&2; exit 1; }
exec 9>/run/lock/sibyl-mysql-backup.lock
flock -n 9 || { echo 'Another backup is running' >&2; exit 1; }
stamp=$(date -u +%Y%m%dT%H%M%SZ)
final="$target/sibyl-mysql-$stamp.sql.gz"
partial="$target/.sibyl-mysql-$stamp.pending"
cleanup(){ rm -f "$partial"; }
trap cleanup EXIT
mysqldump --defaults-extra-file=/etc/sibyl/mysql-root.cnf --single-transaction --routines --triggers --events --hex-blob --set-gtid-purged=OFF --databases sibyl | gzip -9 > "$partial"
gzip -t "$partial"
[[ $(stat -c '%s' "$partial") -gt 100 ]] || { echo 'Backup is unexpectedly small' >&2; exit 1; }
chmod 0600 "$partial"
mv -n "$partial" "$final"
sha256sum "$final" > "$final.sha256"
chmod 0600 "$final.sha256"
(cd "$target" && sha256sum -c "$(basename "$final.sha256")")
find "$target" -maxdepth 1 -type f -name 'sibyl-mysql-*.sql.gz' -mtime "+${RETENTION_DAYS:-30}" -delete
find "$target" -maxdepth 1 -type f -name 'sibyl-mysql-*.sql.gz.sha256' -mtime "+${RETENTION_DAYS:-30}" -delete
echo "Backup successful: $(basename "$final")"
