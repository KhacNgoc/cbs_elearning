param([int]$Port = 8080)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
# Load existing project settings without printing credentials.
foreach ($taskConfig in @('.env.production', '.env')) {
    $taskConfigPath = Join-Path $taskRoot $taskConfig
    if (Test-Path -LiteralPath $taskConfigPath) {
        foreach ($taskConfigLine in Get-Content -LiteralPath $taskConfigPath) {
            if ($taskConfigLine -match '^([A-Z][A-Z0-9_]*)=(.*)$') {
                [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim('"').Trim("'"), 'Process')
            }
        }
    }
}
$env:SERVER_PORT = [string]$Port
$env:SERVER_SERVLET_CONTEXT_PATH = '/'
$env:SPRING_PROFILES_ACTIVE = 'postgres'
# The running container is authoritative for the local database credentials.
$taskContainerSettings = docker inspect --format '{{json .Config.Env}}' coretraining-db | ConvertFrom-Json
if ($LASTEXITCODE -ne 0) { throw 'Unable to read the database container configuration.' }
foreach ($taskSetting in $taskContainerSettings) {
    if ($taskSetting -match '^(POSTGRES_DB|POSTGRES_USER|POSTGRES_PASSWORD)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://localhost:5432/$env:POSTGRES_DB"
$env:SPRING_DATASOURCE_USERNAME = $env:POSTGRES_USER
$env:SPRING_DATASOURCE_PASSWORD = $env:POSTGRES_PASSWORD
$env:DATA_INITIALIZER_ENABLED = 'true'
$env:JWT_COOKIE_SECURE = 'false'
$env:CORS_ALLOWED_ORIGINS = 'http://localhost:5173,http://localhost:4173,http://localhost:4180'
$env:APP_RAG_URL = 'http://localhost:8000'
$env:LOG_PATH = Join-Path $taskRoot 'CoreTrainingAPI/logs'
$env:VIDEO_STORAGE_PATH = Join-Path $taskRoot 'CoreTrainingAPI/uploads/videos'
$env:DOCUMENT_STORAGE_PATH = Join-Path $taskRoot 'CoreTrainingAPI/uploads/documents'
Set-Location (Join-Path $taskRoot 'CoreTrainingAPI')
$taskJavaTemp = Join-Path $taskRoot 'tmp/java'
New-Item -ItemType Directory -Force -Path $taskJavaTemp | Out-Null
& 'C:/Program Files/Java/jdk-17/bin/java.exe' "-Djdk.net.unixdomain.tmpdir=$taskJavaTemp" -jar 'target/coretraining-0.0.1-SNAPSHOT.jar'
