[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$BackupFile,

    [string]$ClientImage = "postgres:17-alpine"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-FullPath([string]$Path) {
    return [System.IO.Path]::GetFullPath($Path)
}

function Invoke-ContainerSql([string]$ContainerName, [string]$Sql) {
    $result = & docker exec $ContainerName psql --no-psqlrc --tuples-only --no-align --set ON_ERROR_STOP=1 --username postgres --dbname schedule_restore --command $Sql
    if ($LASTEXITCODE -ne 0) {
        throw "復元後の検証SQLに失敗しました。"
    }
    return ($result | Out-String).Trim()
}

$backupFullPath = Get-FullPath $BackupFile
if (-not (Test-Path -LiteralPath $backupFullPath -PathType Leaf)) {
    throw "バックアップファイルが見つかりません。"
}

$checksumPath = "$backupFullPath.sha256"
if (-not (Test-Path -LiteralPath $checksumPath -PathType Leaf)) {
    throw "SHA-256ファイルが見つかりません。復元を中止します。"
}
$checksumParts = (Get-Content -LiteralPath $checksumPath -Raw).Trim().Split([char]" ", [System.StringSplitOptions]::RemoveEmptyEntries)
if ($checksumParts.Count -ne 2 -or $checksumParts[0] -notmatch "^[A-Fa-f0-9]{64}$" -or $checksumParts[1] -ne (Split-Path -Leaf $backupFullPath)) {
    throw "SHA-256ファイルの形式または対象ファイル名が不正です。復元を中止します。"
}
$expectedHash = $checksumParts[0]
$actualHash = (Get-FileHash -LiteralPath $backupFullPath -Algorithm SHA256).Hash
if ($expectedHash -ne $actualHash) {
    throw "バックアップのSHA-256が一致しません。復元を中止します。"
}

$backupDirectory = Split-Path -Parent $backupFullPath
$backupFileName = Split-Path -Leaf $backupFullPath
$backupMount = "type=bind,source=$backupDirectory,target=/backup,readonly"
& docker run --rm --mount $backupMount $ClientImage pg_restore --list "/backup/$backupFileName" | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "バックアップをpg_restoreで読み取れません。"
}

$containerName = "schedule-system-restore-" + [Guid]::NewGuid().ToString("N")
$temporaryPassword = [Guid]::NewGuid().ToString("N")
$containerStarted = $false
try {
    & docker run --detach --rm --name $containerName --network none --tmpfs "/var/lib/postgresql/data:rw,noexec,nosuid,size=512m" --env "POSTGRES_PASSWORD=$temporaryPassword" --env "POSTGRES_DB=schedule_restore" $ClientImage | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "隔離復元用PostgreSQLを起動できません。"
    }
    $containerStarted = $true

    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        & docker exec $containerName pg_isready --username postgres --dbname schedule_restore | Out-Null
        if ($LASTEXITCODE -eq 0) {
            $ready = $true
            break
        }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) {
        throw "隔離復元用PostgreSQLの起動がタイムアウトしました。"
    }

    & docker cp $backupFullPath "${containerName}:/tmp/backup.dump"
    if ($LASTEXITCODE -ne 0) {
        throw "バックアップを隔離コンテナへコピーできません。"
    }

    & docker exec $containerName pg_restore --exit-on-error --single-transaction --no-owner --no-acl --username postgres --dbname schedule_restore /tmp/backup.dump
    if ($LASTEXITCODE -ne 0) {
        throw "隔離環境への復元に失敗しました。"
    }

    $requiredTables = "schedule_requests", "calendar_holidays", "schedule_day_offs", "recurring_fixed_request_skips", "flyway_schema_history"
    $quotedTables = ($requiredTables | ForEach-Object { "'$_'" }) -join ","
    $tableCount = [int](Invoke-ContainerSql $containerName "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name IN ($quotedTables);")
    if ($tableCount -ne $requiredTables.Count) {
        throw "必須テーブルが不足しています。"
    }

    $failedMigrations = [int](Invoke-ContainerSql $containerName "SELECT count(*) FROM flyway_schema_history WHERE success = false;")
    if ($failedMigrations -ne 0) {
        throw "Flyway履歴に失敗したMigrationがあります。"
    }

    $requiredConstraints = "ck_schedule_time_range", "ck_schedule_entry_state", "ck_schedule_dispatch_status", "ck_published_schedule_fields", "ck_schedule_time_slots", "ex_schedule_requests_published_time"
    $quotedConstraints = ($requiredConstraints | ForEach-Object { "'$_'" }) -join ","
    $constraintCount = [int](Invoke-ContainerSql $containerName "SELECT count(*) FROM pg_constraint AS constraint_definition INNER JOIN pg_class AS table_definition ON constraint_definition.conrelid = table_definition.oid INNER JOIN pg_namespace AS schema_definition ON table_definition.relnamespace = schema_definition.oid WHERE schema_definition.nspname = 'public' AND table_definition.relname = 'schedule_requests' AND constraint_definition.conname IN ($quotedConstraints);")
    if ($constraintCount -ne $requiredConstraints.Count) {
        throw "必須制約が不足しています。"
    }

    $requestCount = Invoke-ContainerSql $containerName "SELECT count(*) FROM schedule_requests;"
    $holidayCount = Invoke-ContainerSql $containerName "SELECT count(*) FROM calendar_holidays;"
    $dayOffCount = Invoke-ContainerSql $containerName "SELECT count(*) FROM schedule_day_offs;"
    $skipCount = Invoke-ContainerSql $containerName "SELECT count(*) FROM recurring_fixed_request_skips;"
    $migrationCount = [int](Invoke-ContainerSql $containerName "SELECT count(*) FROM flyway_schema_history WHERE success = true;")
    if ($migrationCount -ne 7) {
        throw "Flyway成功履歴が7件ではありません。"
    }

    Write-Host "隔離復元の検証に成功しました。"
    Write-Host "schedule_requests=$requestCount calendar_holidays=$holidayCount schedule_day_offs=$dayOffCount recurring_fixed_request_skips=$skipCount flyway_success=$migrationCount"
}
finally {
    $temporaryPassword = $null
    if ($containerStarted) {
        & docker rm --force $containerName | Out-Null
    }
}
