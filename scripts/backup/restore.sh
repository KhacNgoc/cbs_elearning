#!/usr/bin/env bash
# Khôi phục từ một bản backup restic.
#
#   restore.sh <snapshot> <postgres|uploads|qdrant|all> [--yes] [--delete]
#
# <snapshot> là ID trong "backup.sh snapshots" hoặc "latest".
# postgres: XÓA database hiện tại rồi tạo lại từ bản dump. Backend bị dừng trong lúc khôi phục.
# uploads:  ghi đè file trong volume training_uploads; file mới hơn bản backup được giữ nguyên,
#           trừ khi có --delete (thư mục trở về giống hệt bản backup).
# qdrant:   ghi đè các collection có trong bản backup. Cần Qdrant đang chạy.
# --yes:    không hỏi xác nhận.
# Hướng dẫn: docs/backup-gcs.vi.md, mục "Khôi phục".
set -Eeuo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

usage() {
  sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'
  exit 2
}

SNAPSHOT=${1:-}
TARGET=${2:-}
CONFIRMED=no
UPLOADS_DELETE=()
[ $# -ge 2 ] && shift 2
for opt in "$@"; do
  case "$opt" in
    --yes) CONFIRMED=yes ;;
    --delete) UPLOADS_DELETE=(--delete) ;;
    *) usage ;;
  esac
done
case "$TARGET" in postgres | uploads | qdrant | all) ;; *) usage ;; esac
[ -n "$SNAPSHOT" ] || usage

if [ "$CONFIRMED" != yes ]; then
  echo "Sắp khôi phục '$TARGET' từ snapshot '$SNAPSHOT' vào hệ thống đang chạy tại $REPO_ROOT."
  read -r -p "Gõ 'yes' để tiếp tục: " answer
  [ "$answer" = "yes" ] || { echo "Đã hủy."; exit 1; }
fi
take_lock

STAGING="$BACKUP_RESTORE_DIR/data/staging"

fetch_staging() {
  log "Tải bản dump và snapshot Qdrant từ $SNAPSHOT"
  mkdir -p "$BACKUP_RESTORE_DIR"
  rm -rf "$STAGING"
  compose run --rm -T restore restore "$SNAPSHOT" --target /restore --include /data/staging
}

restore_postgres() {
  local dump="$STAGING/postgres.dump"
  [ -s "$dump" ] || { log "Không có $dump trong bản backup"; return 1; }
  log "Khôi phục PostgreSQL"
  compose exec -T postgres sh -c '
    dropdb -U "$POSTGRES_USER" --if-exists --force "$POSTGRES_DB" &&
    createdb -U "$POSTGRES_USER" -O "$POSTGRES_USER" "$POSTGRES_DB"'
  compose exec -T postgres sh -c \
    'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner --exit-on-error' <"$dump"
}

restore_uploads() {
  log "Khôi phục file upload"
  compose run --rm -T restore restore "$SNAPSHOT" --target / --include /data/uploads \
    ${UPLOADS_DELETE[@]+"${UPLOADS_DELETE[@]}"}
}

restore_qdrant() {
  [ -n "$QDRANT_BACKUP_URL" ] || { log "QDRANT_BACKUP_URL trống, bỏ qua Qdrant"; return; }
  local f c found=0
  for f in "$STAGING"/qdrant/*.snapshot; do
    [ -e "$f" ] || continue
    found=1
    c=$(basename "$f" .snapshot)
    log "Khôi phục collection Qdrant: $c"
    qdrant "/collections/$c/snapshots/upload?priority=snapshot&wait=true" -X POST -F "snapshot=@$f" >/dev/null
  done
  [ "$found" = 1 ] || log "Bản backup không có snapshot Qdrant"
}

trap 'log "THẤT BẠI khi chạy: $BASH_COMMAND. Backend có thể đang dừng: kiểm tra rồi chạy docker compose start backend."' ERR

[ "$TARGET" = uploads ] || fetch_staging

# Chỉ dừng/khởi động lại backend nếu nó đang chạy (máy chủ mới thì chưa có container backend).
BACKEND_WAS_RUNNING=no
if [ "$TARGET" != qdrant ] && [ -n "$(compose ps -q --status running backend 2>/dev/null)" ]; then
  BACKEND_WAS_RUNNING=yes
  log "Dừng backend"
  compose stop backend
fi
case "$TARGET" in
  postgres) restore_postgres ;;
  uploads) restore_uploads ;;
  qdrant) restore_qdrant ;;
  all)
    restore_postgres
    restore_uploads
    restore_qdrant
    ;;
esac
if [ "$BACKEND_WAS_RUNNING" = yes ]; then
  log "Khởi động lại backend"
  compose start backend
fi

rm -rf "$STAGING"
log "Hoàn tất khôi phục $TARGET từ $SNAPSHOT"
