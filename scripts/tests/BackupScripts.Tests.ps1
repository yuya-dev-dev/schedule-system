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
$backupScript = Join-Path $repositoryRoot "scripts/Backup-PostgreSql.ps1"
$restoreScript = Join-Path $repositoryRoot "scripts/Test-PostgreSqlBackup.ps1"
. (Join-Path $repositoryRoot "scripts/PostgreSqlBackup.Common.ps1")

function Assert-Equal([string]$Expected, [string]$Actual, [string]$Label) {
    if ($Expected -cne $Actual) {
        throw "$Label が一致しません。expected=$Expected actual=$Actual"
    }
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

function Write-BackupConfiguration([string]$Path, [string]$JdbcUrl) {
    [pscustomobject]@{
        JdbcUrl = $JdbcUrl
        Username = "backup_test_user"
        Password = ConvertTo-SecureString "fictional-password" -AsPlainText -Force
        OutputDirectory = Join-Path $testRoot "archives"
        RetentionDays = 14
        ClientImage = "postgres:18-alpine"
    } | Export-Clixml -LiteralPath $Path
}

try {
    New-Item -ItemType Directory -Path $testRoot | Out-Null

    Assert-Equal 'fictional\\password\:with-colon' `
        (ConvertTo-PgPassValue 'fictional\password:with-colon') `
        '.pgpassパスワードのエスケープ結果'

    $pooledConfiguration = Join-Path $testRoot "pooled.clixml"
    Write-BackupConfiguration $pooledConfiguration "jdbc:postgresql://fictional-pooler.example.invalid/schedule?sslmode=require"
    Invoke-ScriptExpectingFailure $backupScript @("-ConfigurationPath", $pooledConfiguration) "プーラー接続は使用できません"

    $insecureConfiguration = Join-Path $testRoot "insecure.clixml"
    Write-BackupConfiguration $insecureConfiguration "jdbc:postgresql://fictional.example.invalid/schedule?sslmode=disable"
    Invoke-ScriptExpectingFailure $backupScript @("-ConfigurationPath", $insecureConfiguration) "sslmode=require 以上を指定してください"

    $backupFile = Join-Path $testRoot "schedule-system-test.dump"
    [System.IO.File]::WriteAllText($backupFile, "fictional backup content")
    [System.IO.File]::WriteAllText("$backupFile.sha256", ("0" * 64) + "  " + (Split-Path -Leaf $backupFile))
    Invoke-ScriptExpectingFailure $restoreScript @("-BackupFile", $backupFile) "SHA-256が一致しません"

    [System.IO.File]::WriteAllText("$backupFile.sha256", "invalid checksum metadata")
    Invoke-ScriptExpectingFailure $restoreScript @("-BackupFile", $backupFile) "SHA-256ファイルの形式または対象ファイル名が不正です"

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
