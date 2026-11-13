#!/usr/bin/env bash
# Sao lưu PostgreSQL, Qdrant và file upload vào kho restic (GCS hoặc Garage).
#
#   backup.sh init        tạo kho restic (chạy một lần)
#   backup.sh run         dump DB, snapshot Qdrant, đẩy lên kho (chạy hằng ngày)
#   backup.sh prune       xóa bản cũ theo BACKUP_KEEP_* (hằng tuần)
#   backup.sh check       kiểm tra kho, đọc thử BACKUP_CHECK_SUBSET dữ liệu (hằng tuần)
#   backup.sh snapshots   liệt kê các bản backup
#   backup.sh restic ...  chạy lệnh restic bất kỳ với cấu hình hiện tại
#
# Hướng dẫn: docs/backup-gcs.vi.md
set -Eeuo pipefail
. "$(dirname "${BASH_SOURCE[0]}")/lib.sh"

: "${BACKUP_KEEP_DAILY:=7}"
: "${BACKUP_KEEP_WEEKLY:=4}"
: "${BACKUP_KEEP_MONTHLY:=6}"
: "${BACKUP_CHECK_SUBSET:=5%}"
: "${BACKUP_HEALTHCHECK_URL:=}"

restic_run() { compose run --rm -T backup "$@"; }

notify() {
  [ -n "$BACKUP_HEALTHCHECK_URL" ] || return 0
  curl -fsS -m 10 --retry 3 "$BACKUP_HEALTHCHECK_URL$1" >/dev/null || log "Không gửi được thông báo$1"
}

on_error() {
  log "THẤT BẠI: 'backup.sh $CMD' lỗi khi chạy: $1"
  notify /fail
}

dump_postgres() {
  log "Dump PostgreSQL"
  local tmp="$BACKUP_STAGING_DIR/postgres.dump.tmp"
  compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' >"$tmp"
  # Đọc lại mục lục để chắc file dump không hỏng.
  compose exec -T postgres pg_restore --list <"$tmp" >/dev/null
  mv "$tmp" "$BACKUP_STAGING_DIR/postgres.dump"
  log "PostgreSQL: $(du -h "$BACKUP_STAGING_DIR/postgres.dump" | cut -f1)"
}

dump_qdrant() {
  if [ -z "$QDRANT_BACKUP_URL" ]; then
    log "Bỏ qua Qdrant (QDRANT_BACKUP_URL trống)"
    return
  fi
  local out="$BACKUP_STAGING_DIR/qdrant" collections c name
  mkdir -p "$out"
  rm -f "$out"/*.snapshot
  collections=$(qdrant /collections | { grep -o '"name":"[^"]*"' || true; } | cut -d'"' -f4)
  for c in $collections; do
    log "Snapshot Qdrant: $c"
    name=$(qdrant "/collections/$c/snapshots?wait=true" -X POST | grep -o '"name":"[^"]*"' | head -n1 | cut -d'"' -f4)
    [ -n "$name" ] || { log "Qdrant không trả tên snapshot cho $c"; return 1; }
    qdrant "/collections/$c/snapshots/$name" -o "$out/$c.snapshot"
    # Snapshot nằm trong container Qdrant; xóa sau khi đã tải về.
    qdrant "/collections/$c/snapshots/$name?wait=true" -X DELETE >/dev/null
  done
  [ -n "$collections" ] || log "Qdrant chưa có collection nào"
}

CMD=${1:-}
[ $# -gt 0 ] && shift
trap 'on_error "$BASH_COMMAND"' ERR

case "$CMD" in
  init)
    restic_run init
    ;;
  run)
    take_lock
    notify /start
    mkdir -p "$BACKUP_STAGING_DIR"
    dump_postgres
    dump_qdrant
    log "Đẩy lên kho restic"
    restic_run backup --tag daily /data/staging /data/uploads
    log "Hoàn tất backup"
    notify ""
    ;;
  prune)
    take_lock
    restic_run forget --prune \
      --keep-daily "$BACKUP_KEEP_DAILY" \
      --keep-weekly "$BACKUP_KEEP_WEEKLY" \
      --keep-monthly "$BACKUP_KEEP_MONTHLY"
    ;;
  check)
    take_lock
    restic_run check --read-data-subset="$BACKUP_CHECK_SUBSET"
    ;;
  snapshots)
    restic_run snapshots
    ;;
  restic)
    restic_run "$@"
    ;;
  *)
    sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'
    exit 2
    ;;
esac
