[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repositoryRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0) {
    throw "Gitリポジトリのルートを取得できません。"
}

$powerShellExecutable = (Get-Process -Id $PID).Path
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("schedule-system-backup-tests-" + [Guid]::NewGuid().ToString("N"))
$initializeScript = Join-Path $repositoryRoot "scripts/Initialize-PostgreSqlBackup.ps1"
$backupScript = Join-Path $repositoryRoot "scripts/Backup-PostgreSql.ps1"
$restoreScript = Join-Path $repositoryRoot "scripts/Test-PostgreSqlBackup.ps1"
. (Join-Path $repositoryRoot "scripts/PostgreSqlBackup.Common.ps1")

function Assert-Equal([string]$Expected, [string]$Actual, [string]$Label) {
    if ($Expected -cne $Actual) {
        throw "$Label が一致しません。expected=$Expected actual=$Actual"
    }
}

function Assert-InvalidJdbcUrl([string]$Query, [string]$ExpectedMessage) {
    $caught = $false
    try {
        $null = ConvertFrom-BackupJdbcUrl "jdbc:postgresql://fictional.example.invalid/schedule?$Query"
    }
    catch {
        $caught = $true
        if ($_.Exception.Message -notlike "*$ExpectedMessage*") { throw }
    }
    if (-not $caught) { throw "不正なTLS設定が受理されました: $Query" }
}

function Invoke-ScriptExpectingFailure(
    [string]$ScriptPath,
    [string[]]$ScriptArguments,
    [string]$ExpectedMessage
) {
    $output = (& $powerShellExecutable -NoProfile -File $ScriptPath @ScriptArguments 2>&1 | Out-String)
    if ($LASTEXITCODE -eq 0) {
        throw "失敗を期待したスクリプトが成功しました: $ScriptPath"
    }
    if ($output -notlike "*$ExpectedMessage*") {
        throw "期待したエラーが見つかりません。expected=$ExpectedMessage output=$output"
    }
}

function Write-BackupConfiguration(
    [string]$Path,
    [string]$JdbcUrl,
    [string]$ClientImage = ""
) {
    if ([string]::IsNullOrWhiteSpace($ClientImage)) {
        $ClientImage = Get-ApprovedPostgreSqlClientImage
    }
    [pscustomobject]@{
        JdbcUrl = $JdbcUrl
        Username = "backup_test_user"
        Password = ConvertTo-SecureString "fictional-password" -AsPlainText -Force
        OutputDirectory = Join-Path $testRoot "archives"
        RetentionDays = 14
        ClientImage = $ClientImage
    } | Export-Clixml -LiteralPath $Path
}

try {
    New-Item -ItemType Directory -Path $testRoot | Out-Null

    Assert-Equal 'fictional\\password\:with-colon' `
        (ConvertTo-PgPassValue 'fictional\password:with-colon') `
        '.pgpassパスワードのエスケープ結果'

    Assert-Equal 'verify-full' `
        (ConvertFrom-BackupJdbcUrl 'jdbc:postgresql://fictional.example.invalid/schedule').SslMode `
        'TLS既定値'
    Assert-Equal 'verify-full' `
        (ConvertFrom-BackupJdbcUrl 'jdbc:postgresql://fictional.example.invalid/schedule?SSLMODE=VERIFY%2Dfull').SslMode `
        'TLS値の正規化'
    foreach ($mode in 'disable', 'allow', 'prefer', 'require', 'verify-ca') {
        Assert-InvalidJdbcUrl "sslmode=$mode" 'sslmode=verify-full'
    }
    foreach ($query in 'sslmode=', 'sslmode', 'sslmode=verify-full&sslmode=verify-full',
            'sslmode=verify-full&SSLMODE=require', 'sslmode=verify-full&ssl%6dode=require') {
        Assert-InvalidJdbcUrl $query '空でない値を1つだけ'
    }
    foreach ($query in 'ssl=true', 'ssl=false', 'sslfactory=fictional', 'sslrootcert=fictional',
            'sslcert=fictional', 'channelBinding=require') {
        Assert-InvalidJdbcUrl $query 'TLSオプションはsslmodeだけ'
    }
    Assert-InvalidJdbcUrl 'sslmode=verify-full%26sslmode%3Ddisable' 'sslmodeが不正'
    foreach ($localHost in 'localhost', '127.0.0.1', '[::1]', 'host.docker.internal') {
        $localUrl = "jdbc:postgresql://${localHost}/schedule?sslmode=disable"
        Assert-Equal 'disable' (ConvertFrom-BackupJdbcUrl $localUrl -AllowInsecureLocalTest).SslMode '明示的なローカル例外'
        $rejected = $false
        try { $null = ConvertFrom-BackupJdbcUrl $localUrl }
        catch { $rejected = $_.Exception.Message -like '*sslmode=verify-full*' }
        if (-not $rejected) { throw '例外スイッチなしの非TLS接続が受理されました。' }
    }
    $testHostUrl = 'jdbc:postgresql://isolated-db/schedule?sslmode=disable'
    Assert-Equal 'disable' `
        (ConvertFrom-BackupJdbcUrl $testHostUrl -AllowInsecureLocalTest -LocalTestHost 'isolated-db').SslMode `
        '明示的な隔離DB例外'
    foreach ($arguments in @(
            @{ AllowInsecureLocalTest = $true },
            @{ LocalTestHost = 'isolated-db' },
            @{ AllowInsecureLocalTest = $true; LocalTestHost = 'other-db' })) {
        $rejected = $false
        try { $null = ConvertFrom-BackupJdbcUrl $testHostUrl @arguments }
        catch { $rejected = $_.Exception.Message -like '*sslmode=verify-full*' }
        if (-not $rejected) { throw '不完全な例外指定が受理されました。' }
    }
    Assert-Equal 'verify-full' `
        (ConvertFrom-BackupJdbcUrl 'jdbc:postgresql://isolated-db/schedule' -AllowInsecureLocalTest -LocalTestHost 'isolated-db').SslMode `
        '例外指定時もTLS既定値を維持'

    $unapprovedImageConfiguration = Join-Path $testRoot "unapproved-image.clixml"
    Write-BackupConfiguration `
        $unapprovedImageConfiguration `
        "jdbc:postgresql://fictional.example.invalid/schedule?sslmode=require" `
        "postgres:18-alpine"
    Invoke-ScriptExpectingFailure `
        $backupScript `
        @("-ConfigurationPath", $unapprovedImageConfiguration) `
        "検証済みの固定イメージだけを使用できます"
    Invoke-ScriptExpectingFailure `
        $initializeScript `
        @(
            "-ConfigurationPath", (Join-Path $testRoot "new-config.clixml"),
            "-OutputDirectory", (Join-Path $testRoot "new-archives"),
            "-ClientImage", "postgres:18-alpine",
            "-ApprovedStorage"
        ) `
        "検証済みの固定イメージだけを使用できます"

    $pooledConfiguration = Join-Path $testRoot "pooled.clixml"
    Write-BackupConfiguration $pooledConfiguration "jdbc:postgresql://fictional-pooler.example.invalid/schedule?sslmode=require"
    Invoke-ScriptExpectingFailure $backupScript @("-ConfigurationPath", $pooledConfiguration) "プーラー接続は使用できません"

    $insecureConfiguration = Join-Path $testRoot "insecure.clixml"
    Write-BackupConfiguration $insecureConfiguration "jdbc:postgresql://fictional.example.invalid/schedule?sslmode=disable"
    Invoke-ScriptExpectingFailure $backupScript @("-ConfigurationPath", $insecureConfiguration) "sslmode=verify-full を指定してください"

    foreach ($query in 'sslmode=require', 'sslmode=verify-ca', 'sslmode=verify-full&ssl%6dode=disable') {
        Write-BackupConfiguration $insecureConfiguration "jdbc:postgresql://fictional.example.invalid/schedule?$query"
        $capture = [pscustomobject]@{ Invoked = $false }
        function docker { $capture.Invoked = $true; throw 'Dockerを呼び出してはいけません。' }
        $rejected = $false
        try { & $backupScript -ConfigurationPath $insecureConfiguration }
        catch { $rejected = $_.Exception.Message -like '*sslmode*' }
        finally { Remove-Item Function:docker }
        if (-not $rejected -or $capture.Invoked) { throw 'TLS設定をDocker起動前に拒否できませんでした。' }
    }

    Write-BackupConfiguration $insecureConfiguration 'jdbc:postgresql://fictional.example.invalid/schedule'
    $capture = [pscustomobject]@{ Arguments = @() }
    function docker { $capture.Arguments = $args; throw 'TLS引数の検査のため停止' }
    try { & $backupScript -ConfigurationPath $insecureConfiguration }
    catch { if ($_.Exception.Message -ne 'TLS引数の検査のため停止') { throw } }
    finally { Remove-Item Function:docker }
    foreach ($expectedArgument in 'PGSSLROOTCERT=system', 'PGSSLMODE=verify-full') {
        if ($expectedArgument -cnotin $capture.Arguments) { throw "TLS引数がありません: $expectedArgument" }
    }

    function Read-Host { return 'jdbc:postgresql://fictional.example.invalid/schedule?sslmode=require' }
    function Get-Credential { throw 'TLS検証前に資格情報を取得してはいけません。' }
    $rejected = $false
    try {
        & $initializeScript -ConfigurationPath (Join-Path $testRoot 'invalid-initial-config.clixml') `
            -OutputDirectory (Join-Path $testRoot 'initial-archives') -ApprovedStorage
    }
    catch { $rejected = $_.Exception.Message -like '*sslmode=verify-full*' }
    finally { Remove-Item Function:Read-Host; Remove-Item Function:Get-Credential }
    if (-not $rejected -or (Test-Path -LiteralPath (Join-Path $testRoot 'invalid-initial-config.clixml'))) {
        throw '初期設定で弱いTLS設定を資格情報取得・保存前に拒否できませんでした。'
    }

    $composeOutput = (& docker compose config --format json | Out-String)
    if ($LASTEXITCODE -ne 0) { throw 'Compose設定を解決できません。' }
    $compose = $composeOutput | ConvertFrom-Json
    $ports = @($compose.services.'schedule-system'.ports)
    if ($ports.Count -ne 1 -or $ports[0].host_ip -cne '127.0.0.1' -or $ports[0].target -ne 8080) {
        throw 'ローカルComposeの公開先がloopbackに限定されていません。'
    }

    $backupFile = Join-Path $testRoot "schedule-system-test.dump"
    [System.IO.File]::WriteAllText($backupFile, "fictional backup content")
    [System.IO.File]::WriteAllText("$backupFile.sha256", ("0" * 64) + "  " + (Split-Path -Leaf $backupFile))
    Invoke-ScriptExpectingFailure $restoreScript @("-BackupFile", $backupFile) "SHA-256が一致しません"

    [System.IO.File]::WriteAllText("$backupFile.sha256", "invalid checksum metadata")
    Invoke-ScriptExpectingFailure $restoreScript @("-BackupFile", $backupFile) "SHA-256ファイルの形式または対象ファイル名が不正です"

    Invoke-ScriptExpectingFailure `
        $restoreScript `
        @("-BackupFile", $backupFile, "-ClientImage", "postgres:18-alpine") `
        "検証済みの固定イメージだけを使用できます"

    Write-Host "バックアップスクリプトの回帰テストに成功しました。"
}
finally {
    $temporaryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    $testFullPath = [System.IO.Path]::GetFullPath($testRoot)
    $testDirectoryName = Split-Path -Leaf $testFullPath
    $insideTemporaryDirectory = $testFullPath.StartsWith(
        $temporaryRoot, [System.StringComparison]::OrdinalIgnoreCase)
    $hasExpectedDirectoryName = $testDirectoryName.StartsWith("schedule-system-backup-tests-")
    if ($insideTemporaryDirectory -and $hasExpectedDirectoryName) {
        Remove-Item -LiteralPath $testFullPath -Recurse -Force -ErrorAction SilentlyContinue
    }
}

exit 0
