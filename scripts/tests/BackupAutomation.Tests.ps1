#Requires -Version 7.0
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../PostgreSqlBackup.Automation.ps1')
. (Join-Path $PSScriptRoot '../PostgreSqlBackup.Common.ps1')
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('schedule-system-automation-tests-' + [Guid]::NewGuid().ToString('N'))
$configurationPath = Join-Path $testRoot 'config.clixml'
$archives = Join-Path $testRoot 'archives'
$statePath = Join-Path $archives '.daily-backup-status.json'
$scenario = [pscustomobject]@{ Online = $true; DockerReady = $true; ImageReady = $true; Fail = $false; Calls = 0; StartedDocker = $false; Stops = 0; StopResult = 'Stopped' }
$previousExitCode = Get-Variable LASTEXITCODE -Scope Global -ValueOnly -ErrorAction SilentlyContinue

function Assert-Equal($Expected, $Actual, [string]$Label) {
    if ($Expected -cne $Actual) { throw "$Label expected=$Expected actual=$Actual" }
}
function Test-BackupAutomationNetwork { return $scenario.Online }
function Test-BackupAutomationDocker {
    param([switch]$StartDockerDesktop, [ref]$StartedByBackup)
    $StartedByBackup.Value = $scenario.StartedDocker
    return $scenario.DockerReady
}
function Stop-BackupAutomationOwnedDocker {
    Assert-Equal 'Succeeded' ((Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json).Status) '停止処理前に取得成功を永続化'
    $scenario.Stops++
    return $scenario.StopResult
}
function Invoke-BackupAutomationDockerCommand([string[]]$Arguments, [int]$TimeoutSeconds = 15) {
    if ($Arguments[0] -ne 'image' -or $Arguments[1] -ne 'inspect') { throw '想定外のDocker呼出しです。' }
    return $(if ($scenario.ImageReady) { 0 } else { 1 })
}
function Invoke-BackupAutomationCommand([string]$ConfigurationPath) {
    $scenario.Calls++
    if ($scenario.Fail) { throw 'fictional secret must not appear in status output' }
    $name = 'schedule-system-' + [Guid]::NewGuid().ToString('N') + '.dump'
    $path = Join-Path $archives $name
    [System.IO.File]::WriteAllText($path, 'fictional backup content')
    $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
    [System.IO.File]::WriteAllText("$path.sha256", "$hash  $name`n")
    return [pscustomobject]@{ BackupFile = $path; Sha256 = $hash }
}

try {
    New-Item -ItemType Directory -Path $testRoot | Out-Null
    [pscustomobject]@{
        OutputDirectory = $archives
        JdbcUrl = 'jdbc:postgresql://fictional.example.invalid/schedule?sslmode=verify-full'
        ClientImage = Get-ApprovedPostgreSqlClientImage
    } | Export-Clixml -LiteralPath $configurationPath
    $scenario.Online = $false
    Assert-Equal 'Offline' (Invoke-DailyPostgreSqlBackup $configurationPath) 'オフライン'
    Assert-Equal 0 $scenario.Calls 'オフライン時の取得回数'
    $scenario.Online = $true
    $scenario.DockerReady = $false
    Assert-Equal 'DockerUnavailable' (Invoke-DailyPostgreSqlBackup $configurationPath) 'Docker停止'
    Assert-Equal 'DockerNotReady' ((Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json).FailureCode) 'Docker失敗を識別'
    $scenario.DockerReady = $true
    $scenario.ImageReady = $false
    Assert-Equal 'Failed' (Invoke-DailyPostgreSqlBackup $configurationPath) '未取得イメージを自動pullしない'
    Assert-Equal 'ClientImageUnavailable' ((Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json).FailureCode) 'イメージ未取得を識別'
    Assert-Equal 0 $scenario.Calls '失敗時の取得回数'
    $scenario.ImageReady = $true
    $scenario.Fail = $true
    Assert-Equal 'Failed' (Invoke-DailyPostgreSqlBackup $configurationPath) '接続失敗'
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json -AsHashtable
    Assert-Equal 'BackupFailed' $state.FailureCode '取得失敗を識別'
    if ($state.ContainsKey('SuccessDate') -or (Get-Content -LiteralPath $statePath -Raw) -like '*fictional secret*') {
        throw '失敗を成功として記録したか、秘密値を保存しました。'
    }
    $scenario.Fail = $false
    Assert-Equal 'Succeeded' (Invoke-DailyPostgreSqlBackup $configurationPath) '失敗後の再試行'
    Assert-Equal $null ((Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json).FailureCode) '成功時に失敗理由を解除'
    Assert-Equal 2 $scenario.Calls '失敗を含む取得回数'
    $scenario.Online = $false
    $scenario.DockerReady = $false
    Assert-Equal 'AlreadyCompleted' (Invoke-DailyPostgreSqlBackup $configurationPath) '同日重複防止'
    Assert-Equal 2 $scenario.Calls '成功済みならDockerもネットも不要'
    Assert-Equal 0 $scenario.Stops '元から起動していたDockerを停止しない'
    $scenario.Online = $true
    $scenario.DockerReady = $true
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json -AsHashtable
    $state.SuccessDate = (Get-Date).AddDays(-1).ToString('yyyy-MM-dd')
    [System.IO.File]::WriteAllText($statePath, ($state | ConvertTo-Json))
    Assert-Equal 'Succeeded' (Invoke-DailyPostgreSqlBackup $configurationPath) '翌日の取得'

    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json -AsHashtable
    [System.IO.File]::WriteAllText((Join-Path $archives $state.BackupName), 'corrupted fictional backup')
    Assert-Equal 'Succeeded' (Invoke-DailyPostgreSqlBackup $configurationPath) '成功済みファイル破損時の再取得'
    [System.IO.File]::AppendAllText($configurationPath, [Environment]::NewLine)
    Assert-Equal 'Succeeded' (Invoke-DailyPostgreSqlBackup $configurationPath) '設定変更時の再取得'
    [System.IO.File]::WriteAllText($statePath, '{broken json')
    Assert-Equal 'Succeeded' (Invoke-DailyPostgreSqlBackup $configurationPath) '状態ファイル破損時の回復'

    $lock = [System.IO.File]::Open((Join-Path $archives '.daily-backup.lock'),
        [System.IO.FileMode]::Open, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
    try { Assert-Equal 'Busy' (Invoke-DailyPostgreSqlBackup $configurationPath) '同時実行防止' }
    finally { $lock.Dispose() }
    $rejected = $false
    try { $null = Assert-BackupAutomationPath (Join-Path $PSScriptRoot 'forbidden.dump') }
    catch { $rejected = $true }
    if (-not $rejected) { throw 'リポジトリ内の保存先が受理されました。' }
    $scenario.StartedDocker = $true
    [System.IO.File]::WriteAllText($statePath, '{}')
    Assert-Equal 'Succeeded' (Invoke-DailyPostgreSqlBackup $configurationPath) '自動起動したDockerで取得'
    Assert-Equal 1 $scenario.Stops '自分で起動したDockerだけ成功後に停止'
    Assert-Equal 'Stopped' ((Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json).DockerCleanupStatus) '停止結果を記録'
    Assert-Equal 'AlreadyCompleted' (Invoke-DailyPostgreSqlBackup $configurationPath) '取得済みなら停止処理も呼ばない'
    Assert-Equal 1 $scenario.Stops '二度目は停止しない'
    $scenario.Fail = $true
    [System.IO.File]::WriteAllText($statePath, '{}')
    Assert-Equal 'Failed' (Invoke-DailyPostgreSqlBackup $configurationPath) '取得失敗'
    Assert-Equal 1 $scenario.Stops '取得失敗時は停止しない'
    $scenario.Fail = $false
    $scenario.StopResult = 'StopFailed'
    Assert-Equal 'Succeeded' (Invoke-DailyPostgreSqlBackup $configurationPath) 'Docker停止失敗でも取得成功を維持'
    Assert-Equal 'StopFailed' ((Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json).DockerCleanupStatus) '停止失敗を別に記録'
    $interruptionScript = Join-Path $testRoot 'interrupted-cleanup.ps1'
    $interruptionFixture = @'
param([string]$AutomationPath, [string]$ConfigurationPath)
$ErrorActionPreference = 'Stop'
. $AutomationPath
function Test-BackupAutomationNetwork { return $true }
function Test-BackupAutomationDocker {
    param([switch]$StartDockerDesktop, [ref]$StartedByBackup)
    $StartedByBackup.Value = $true
    return $true
}
function Invoke-BackupAutomationDockerCommand { return 0 }
function Invoke-BackupAutomationCommand([string]$ConfigurationPath) {
    $config = Import-Clixml -LiteralPath $ConfigurationPath
    $path = Join-Path $config.OutputDirectory ('schedule-system-' + [Guid]::NewGuid().ToString('N') + '.dump')
    [System.IO.File]::WriteAllText($path, 'fictional interrupted cleanup backup')
    $hash = (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
    [System.IO.File]::WriteAllText("$path.sha256", "$hash  $(Split-Path -Leaf $path)`n")
    return [pscustomobject]@{ BackupFile = $path; Sha256 = $hash }
}
function Stop-BackupAutomationOwnedDocker { [Environment]::Exit(43) }
$null = Invoke-DailyPostgreSqlBackup $ConfigurationPath
'@
    [System.IO.File]::WriteAllText($interruptionScript, $interruptionFixture)
    [System.IO.File]::WriteAllText($statePath, '{}')
    & (Get-Process -Id $PID).Path -NoProfile -File $interruptionScript `
        -AutomationPath (Join-Path $PSScriptRoot '../PostgreSqlBackup.Automation.ps1') -ConfigurationPath $configurationPath
    Assert-Equal 43 $LASTEXITCODE 'Docker終了処理中のプロセス中断を再現'
    $interruptedState = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    Assert-Equal 'Succeeded' $interruptedState.Status '中断しても取得成功記録を保持'
    Assert-Equal 'Pending' $interruptedState.DockerCleanupStatus '終了未完了を区別'
    $callsBefore = $scenario.Calls
    Assert-Equal 'AlreadyCompleted' (Invoke-DailyPostgreSqlBackup $configurationPath) '終了処理中断後も重複取得しない'
    Assert-Equal $callsBefore $scenario.Calls '中断後の取得回数は増えない'
    if ($IsWindows) {
        $identity = [System.Security.Principal.WindowsIdentity]::GetCurrent()
        Assert-Equal $true (Test-BackupAutomationTaskOwner $identity.Name $identity.User.Value) 'アカウント名のSID正規化'
        Assert-Equal $true (Test-BackupAutomationTaskOwner $identity.User.Value $identity.User.Value) 'SID形式の所有者判定'
        Assert-Equal $false (Test-BackupAutomationTaskOwner 'S-1-5-18' $identity.User.Value) '別ユーザーのタスクを拒否'
    }
    Write-Host '自動バックアップの対象限定テストに成功しました。本番接続・タスク登録は行っていません。'
}
finally {
    $global:LASTEXITCODE = $previousExitCode
    $temporaryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    $testFullPath = [System.IO.Path]::GetFullPath($testRoot)
    if ($testFullPath.StartsWith($temporaryRoot, [System.StringComparison]::OrdinalIgnoreCase) -and
            (Split-Path -Leaf $testFullPath).StartsWith('schedule-system-automation-tests-')) {
        Remove-Item -LiteralPath $testFullPath -Recurse -Force -ErrorAction SilentlyContinue
    }
}
