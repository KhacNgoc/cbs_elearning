# Dùng chung cho backup.sh và restore.sh. Không chạy trực tiếp.

# pwd -W: đường dẫn dạng Windows khi chạy bằng Git Bash; trên Linux lệnh này lỗi và dùng pwd.
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && { pwd -W 2>/dev/null || pwd; })"
BACKUP_CONFIG_DIR="${BACKUP_CONFIG_DIR:-/etc/cbs-elearning-backup}"

if [ ! -r "$BACKUP_CONFIG_DIR/backup.env" ]; then
  echo "Không đọc được $BACKUP_CONFIG_DIR/backup.env (đặt BACKUP_CONFIG_DIR nếu để chỗ khác)" >&2
  exit 1
fi
set -a
# shellcheck disable=SC1091
. "$BACKUP_CONFIG_DIR/backup.env"
set +a

: "${APP_ENV_FILE:=$REPO_ROOT/.env.production}"
: "${BACKUP_STAGING_DIR:=/var/backups/cbs-elearning/staging}"
: "${BACKUP_RESTORE_DIR:=/var/backups/cbs-elearning/restore}"
# Để trống nếu không chạy profile chatbot (không có Qdrant).
: "${QDRANT_BACKUP_URL=http://127.0.0.1:6333}"
: "${QDRANT_API_KEY:=}"
export BACKUP_CONFIG_DIR BACKUP_STAGING_DIR BACKUP_RESTORE_DIR

log() { echo "$(date '+%F %T') $*"; }

compose() {
  docker compose --project-directory "$REPO_ROOT" --env-file "$APP_ENV_FILE" \
    -f "$REPO_ROOT/docker-compose.yml" -f "$REPO_ROOT/docker-compose.backup.yml" "$@"
}

qdrant() {
  local path=$1
  shift
  if [ -n "$QDRANT_API_KEY" ]; then
    curl -fsS -H "api-key: $QDRANT_API_KEY" "$@" "$QDRANT_BACKUP_URL$path"
  else
    curl -fsS "$@" "$QDRANT_BACKUP_URL$path"
  fi
}

# Chỉ một lệnh backup/restore chạy tại một thời điểm.
take_lock() {
  if command -v flock >/dev/null; then
    exec 9>"${BACKUP_LOCK_FILE:-/tmp/cbs-elearning-backup.lock}"
    flock -n 9 || { log "Đang có lệnh backup/restore khác chạy, dừng."; exit 1; }
  fi
}
