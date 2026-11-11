#!/usr/bin/env bash
# Nightly backup of the Locker24 MDM server: database dump plus the uploaded
# files and plugin files. Keeps 14 days of dated folders under /root/backups.
# Install: copy to /opt/locker24/backup.sh (chmod 700) and add to root's crontab:
#   30 3 * * * /opt/locker24/backup.sh >> /var/log/locker24-backup.log 2>&1
set -euo pipefail

BACKUP_ROOT=/root/backups
WORK_DIR=/var/lib/tomcat10/work
DB_NAME=hmdm
KEEP_DAYS=14

DAY=$(date +%F)
DEST="$BACKUP_ROOT/$DAY"

echo "$(date -Is) backup start -> $DEST"
umask 077
mkdir -p "$DEST"

cd /tmp
sudo -u postgres pg_dump -Fc "$DB_NAME" > "$DEST/$DB_NAME.dump"
test -s "$DEST/$DB_NAME.dump"

tar -C "$WORK_DIR" -czf "$DEST/files.tar.gz" files
tar -C "$WORK_DIR" -czf "$DEST/plugins.tar.gz" plugins

find "$BACKUP_ROOT" -mindepth 1 -maxdepth 1 -type d \
    -regex '.*/[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]' \
    -mtime +"$KEEP_DAYS" -exec rm -rf {} +

du -sh "$DEST"
echo "$(date -Is) backup done"
