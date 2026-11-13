#!/usr/bin/env bash
# Chạy trên MÁY CHỦ BACKUP. Tạo snapshot Btrfs chỉ-đọc của toàn bộ dữ liệu Garage (meta + data)
# và xóa snapshot cũ hơn GARAGE_SNAPSHOT_KEEP_DAYS ngày.
#
# Garage không có object lock: nếu máy chủ ứng dụng bị chiếm và kho restic bị xóa qua S3,
# các snapshot này (chỉ root máy chủ backup xóa được) là nơi lấy lại dữ liệu.
# Hướng dẫn: docs/backup-garage.vi.md, mục 2.6 và 6.2.
set -euo pipefail

STORE=${GARAGE_STORE:-/srv/garage/store}
SNAP_DIR=${GARAGE_SNAPSHOT_DIR:-/srv/garage/.snapshots}
KEEP_DAYS=${GARAGE_SNAPSHOT_KEEP_DAYS:-30}

log() { echo "$(date '+%F %T') $*"; }

mkdir -p "$SNAP_DIR"
name="store-$(date +%Y%m%d-%H%M%S)"
btrfs subvolume snapshot -r "$STORE" "$SNAP_DIR/$name"
log "Đã tạo $SNAP_DIR/$name"

cutoff=$(date -d "-$KEEP_DAYS days" +%Y%m%d)
for s in "$SNAP_DIR"/store-*; do
  [ -d "$s" ] || continue
  day=$(basename "$s" | cut -d- -f2)
  if [ "$day" -lt "$cutoff" ]; then
    btrfs subvolume delete "$s"
    log "Đã xóa $s"
  fi
done
