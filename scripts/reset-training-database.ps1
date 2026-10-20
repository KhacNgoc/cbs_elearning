$ErrorActionPreference = 'Stop'
# Explicit, local project target. Never resolves a remote production connection.
$taskContainer = 'coretraining-db'
$taskDatabaseCommand = 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"'
docker inspect --format '{{.Name}}' $taskContainer
if ($LASTEXITCODE -ne 0) { throw 'Không tìm thấy database container của dự án.' }
docker exec $taskContainer sh -c $taskDatabaseCommand
if ($LASTEXITCODE -ne 0) { throw 'Reset database thất bại.' }
Write-Output 'Đã reset schema public của database dự án. Khởi động API để tạo schema và dữ liệu v3.'
