param(
    [ValidateRange(1, 65535)]
    [int]$Port = 8080,
    [string]$JavaHome = '',
    [string]$MavenExecutable = 'mvn.cmd'
)
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

# Use the same JDK for Maven and the application.
if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    if (Test-Path -LiteralPath 'C:/Program Files/Java/jdk-17/bin/java.exe') {
        $JavaHome = 'C:/Program Files/Java/jdk-17'
    } else {
        $JavaHome = $env:JAVA_HOME
    }
}
if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    throw 'JDK not found. Supply -JavaHome with the path to JDK 17.'
}
$taskJava = Join-Path $JavaHome 'bin/java.exe'
if (!(Test-Path -LiteralPath $taskJava)) {
    throw "Java executable not found: $taskJava"
}
$env:JAVA_HOME = $JavaHome
$env:PATH = "$(Join-Path $JavaHome 'bin');$env:PATH"
if ($MavenExecutable -eq 'mvn') {
    $MavenExecutable = 'mvn.cmd'
}
$taskMaven = Get-Command $MavenExecutable -CommandType Application -ErrorAction SilentlyContinue |
    Select-Object -First 1
if ($null -eq $taskMaven -and $MavenExecutable -eq 'mvn.cmd') {
    # A terminal opened before Maven was installed still has the old PATH.
    $taskUserPath = [Environment]::GetEnvironmentVariable('Path', 'User')
    $env:PATH = "$env:PATH;$taskUserPath"
    $taskMaven = Get-Command $MavenExecutable -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
}
if ($null -eq $taskMaven) {
    throw 'Maven not found. Install Maven and add its bin directory to PATH, or supply -MavenExecutable with the full path to mvn.cmd.'
}
$taskMavenPath = $taskMaven.Source
if ($null -eq (Get-Command docker -CommandType Application -ErrorAction SilentlyContinue)) {
    throw 'Docker not found. Install Docker Desktop and start the coretraining-db container.'
}
$taskListener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($taskListener) {
    throw "Port $Port is already in use. Stop the existing backend before restarting, or choose another -Port."
}

$env:SERVER_PORT = [string]$Port
$env:SERVER_SERVLET_CONTEXT_PATH = '/'
$env:SPRING_PROFILES_ACTIVE = 'postgres'
# The running container is authoritative for the local database credentials.
$taskContainerJson = & docker inspect --format '{{json .Config.Env}}' coretraining-db
if ($LASTEXITCODE -ne 0) { throw 'Unable to read the database container configuration.' }
$taskContainerSettings = $taskContainerJson | ConvertFrom-Json
foreach ($taskSetting in $taskContainerSettings) {
    if ($taskSetting -match '^(POSTGRES_DB|POSTGRES_USER|POSTGRES_PASSWORD)=(.*)$') {
        [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
    }
}
foreach ($taskRequiredSetting in @('POSTGRES_DB', 'POSTGRES_USER', 'POSTGRES_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($taskRequiredSetting, 'Process'))) {
        throw "Database setting $taskRequiredSetting is missing. Check the coretraining-db container configuration."
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
$taskJavaTemp = Join-Path $taskRoot 'tmp/java'
New-Item -ItemType Directory -Force -Path $taskJavaTemp | Out-Null
Push-Location (Join-Path $taskRoot 'CoreTrainingAPI')
try {
    # Always rebuild: the CSRF endpoint must come from the current source.
    Write-Host 'Building backend from current source...'
    & $taskMavenPath '-B' 'clean' 'package' '-DskipTests'
    if ($LASTEXITCODE -ne 0) {
        throw 'Backend build failed. The application was not started.'
    }
    $taskJar = Join-Path (Get-Location).Path 'target/coretraining-0.0.1-SNAPSHOT.jar'
    if (!(Test-Path -LiteralPath $taskJar)) {
        throw "Built JAR not found: $taskJar"
    }
    Write-Host "Starting backend on port $Port..."
    & $taskJava "-Djdk.net.unixdomain.tmpdir=$taskJavaTemp" -jar $taskJar
    if ($LASTEXITCODE -ne 0) {
        throw "Backend exited with code $LASTEXITCODE. Check the application logs."
    }
} finally {
    Pop-Location
}
