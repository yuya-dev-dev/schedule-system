[CmdletBinding()]
param(
    [string]$ApplicationImage = "schedule-system:ci",
    [string]$PostgreSqlImage = "postgres:18-alpine"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repositoryRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0) {
    throw "Gitリポジトリのルートを取得できません。"
}

$executionId = [Guid]::NewGuid().ToString("N")
$networkName = "schedule-system-ci-$executionId"
$databaseContainer = "schedule-system-db-$executionId"
$applicationContainer = "schedule-system-app-$executionId"
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) "schedule-system-backup-smoke-$executionId"
$configurationPath = Join-Path $testRoot "backup.clixml"
$outputDirectory = Join-Path $testRoot "archives"
$databaseUser = "schedule_ci"
$databasePassword = 'fictional\password:with-colon'
$databaseName = "schedule"
$networkCreated = $false
$databaseStarted = $false
$applicationStarted = $false

function Invoke-Docker([string[]]$Arguments, [string]$FailureMessage) {
    & docker @Arguments | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw $FailureMessage
    }
}

try {
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null

    Invoke-Docker @("network", "create", $networkName) "一時Dockerネットワークを作成できません。"
    $networkCreated = $true

    Invoke-Docker @(
        "run", "--detach", "--name", $databaseContainer,
        "--network", $networkName,
        "--tmpfs", "/var/lib/postgresql:rw,noexec,nosuid,size=512m",
        "--env", "POSTGRES_USER=$databaseUser",
        "--env", "POSTGRES_PASSWORD=$databasePassword",
        "--env", "POSTGRES_DB=$databaseName",
        $PostgreSqlImage
    ) "一時PostgreSQLを起動できません。"
    $databaseStarted = $true

    $databaseReady = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        & docker exec $databaseContainer pg_isready --username $databaseUser --dbname $databaseName | Out-Null
        if ($LASTEXITCODE -eq 0) {
            $databaseReady = $true
            break
        }
        $running = (& docker inspect --format "{{.State.Running}}" $databaseContainer 2>$null | Out-String).Trim()
        if ($running -ne "true") {
            $logs = (& docker logs $databaseContainer 2>&1 | Out-String)
            throw "一時PostgreSQLが起動前に終了しました。logs=$logs"
        }
        Start-Sleep -Seconds 1
    }
    if (-not $databaseReady) {
        throw "一時PostgreSQLの起動がタイムアウトしました。"
    }

    Invoke-Docker @(
        "run", "--detach", "--name", $applicationContainer,
        "--network", $networkName,
        "--env", "SPRING_PROFILES_ACTIVE=cloud",
        "--env", "SPRING_DATASOURCE_URL=jdbc:postgresql://${databaseContainer}:5432/$databaseName",
        "--env", "SPRING_DATASOURCE_USERNAME=$databaseUser",
        "--env", "SPRING_DATASOURCE_PASSWORD=$databasePassword",
        "--env", "SCHEDULE_ACCESS_PASSWORD=fictional-ci-access-password",
        "--env", "SCHEDULE_HOLIDAYS_SYNC_ENABLED=false",
        "--env", "SCHEDULE_RETENTION_ENABLED=false",
        "--env", "SCHEDULE_MAINTENANCE_ENABLED=false",
        $ApplicationImage
    ) "CI用アプリケーションコンテナを起動できません。"
    $applicationStarted = $true

    $applicationReady = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $logs = (& docker logs $applicationContainer 2>&1 | Out-String)
        if ($logs -like "*Started ScheduleSystemApplication*") {
            $applicationReady = $true
            break
        }
        $running = (& docker inspect --format "{{.State.Running}}" $applicationContainer 2>$null | Out-String).Trim()
        if ($running -ne "true") {
            throw "CI用アプリケーションが起動前に終了しました。logs=$logs"
        }
        Start-Sleep -Seconds 1
    }
    if (-not $applicationReady) {
        throw "CI用アプリケーションの起動がタイムアウトしました。"
    }

    Invoke-Docker @("rm", "--force", $applicationContainer) "CI用アプリケーションを停止できません。"
    $applicationStarted = $false

    [pscustomobject]@{
        JdbcUrl = "jdbc:postgresql://${databaseContainer}:5432/${databaseName}?sslmode=disable"
        Username = $databaseUser
        Password = ConvertTo-SecureString $databasePassword -AsPlainText -Force
        OutputDirectory = $outputDirectory
        RetentionDays = 14
        ClientImage = $PostgreSqlImage
    } | Export-Clixml -LiteralPath $configurationPath

    & (Join-Path $repositoryRoot "scripts/Backup-PostgreSql.ps1") `
        -ConfigurationPath $configurationPath `
        -AllowInsecureLocalTest `
        -LocalTestHost $databaseContainer `
        -ClientNetwork $networkName
    if ($LASTEXITCODE -ne 0) {
        throw "一時PostgreSQLからのバックアップに失敗しました。"
    }

    $backups = @(Get-ChildItem -LiteralPath $outputDirectory -Filter "schedule-system-*.dump" -File)
    if ($backups.Count -ne 1) {
        throw "成功したバックアップが1件ではありません。"
    }

    & (Join-Path $repositoryRoot "scripts/Test-PostgreSqlBackup.ps1") `
        -BackupFile $backups[0].FullName `
        -ClientImage $PostgreSqlImage
    if ($LASTEXITCODE -ne 0) {
        throw "バックアップの隔離復元確認に失敗しました。"
    }

    Write-Host "Dockerイメージ、バックアップ、隔離復元のスモークテストに成功しました。"
}
finally {
    $databasePassword = $null
    if ($applicationStarted) {
        & docker rm --force $applicationContainer | Out-Null
    }
    if ($databaseStarted) {
        & docker rm --force --volumes $databaseContainer | Out-Null
    }
    if ($networkCreated) {
        & docker network rm $networkName | Out-Null
    }

    $temporaryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    $testFullPath = [System.IO.Path]::GetFullPath($testRoot)
    if ($testFullPath.StartsWith($temporaryRoot, [System.StringComparison]::OrdinalIgnoreCase) -and
            (Split-Path -Leaf $testFullPath).StartsWith("schedule-system-backup-smoke-")) {
        Remove-Item -LiteralPath $testFullPath -Recurse -Force -ErrorAction SilentlyContinue
    }
}

exit 0
