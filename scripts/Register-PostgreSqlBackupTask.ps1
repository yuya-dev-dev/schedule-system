#Requires -Version 7.0
[CmdletBinding(SupportsShouldProcess)]
param(
    [string]$ConfigurationPath = (Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'ScheduleSystem/backup-config.clixml'),
    [switch]$Enable,
    [switch]$InitialRestoreVerified
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if (-not $IsWindows) { throw 'タスク登録はWindows上のPowerShell 7で実行してください。' }
. (Join-Path $PSScriptRoot 'PostgreSqlBackup.Automation.ps1')
. (Join-Path $PSScriptRoot 'PostgreSqlBackup.Common.ps1')
$configurationFullPath = Assert-BackupAutomationPath $ConfigurationPath
if ($Enable) {
    if (-not $InitialRestoreVerified) { throw '初回取得と隔離復元の成功後に -InitialRestoreVerified を指定してください。' }
    if (-not (Test-Path -LiteralPath $configurationFullPath -PathType Leaf)) { throw 'バックアップの初期設定が未完了です。' }
    $configuration = Import-Clixml -LiteralPath $configurationFullPath
    $null = Assert-BackupAutomationPath $configuration.OutputDirectory
    $null = ConvertFrom-BackupJdbcUrl $configuration.JdbcUrl
    $null = Resolve-ApprovedPostgreSqlClientImage $configuration.ClientImage
}
$taskName = 'ScheduleSystem-DailyBackup'
$description = 'schedule-system local daily backup automation'
$launcherPath = Join-Path $PSScriptRoot 'Run-PostgreSqlBackupHidden.js'
$workingDirectory = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$userId = [System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value
$powerShell = (Get-Command pwsh.exe -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
$scriptHost = Join-Path $env:WINDIR 'System32/wscript.exe'
if (-not (Test-Path -LiteralPath $scriptHost -PathType Leaf) -or
        -not (Test-Path -LiteralPath $launcherPath -PathType Leaf)) {
    throw '非表示起動用のWindows Script Hostまたはランチャーが見つかりません。'
}
foreach ($path in @($launcherPath, $powerShell, $configurationFullPath)) {
    if ($path -match '["%\r\n]') { throw '起動用パスに使用できない文字が含まれています。' }
}
$arguments = "//B //NoLogo `"$launcherPath`" `"$powerShell`" `"$configurationFullPath`""
$action = New-ScheduledTaskAction -Execute $scriptHost -Argument $arguments -WorkingDirectory $workingDirectory
$triggers = @(
    (New-ScheduledTaskTrigger -AtLogOn -User $userId),
    (New-ScheduledTaskTrigger -Once -At (Get-Date).AddHours(3) -RepetitionInterval (New-TimeSpan -Hours 3))
)
$eventClass = Get-CimClass -Namespace 'Root/Microsoft/Windows/TaskScheduler' -ClassName 'MSFT_TaskEventTrigger'
$networkTrigger = New-CimInstance -ClientOnly -CimClass $eventClass -Property @{
        Enabled = $true
        Delay = 'PT30S'
        Subscription = '<QueryList><Query Id="0" Path="Microsoft-Windows-NetworkProfile/Operational"><Select Path="Microsoft-Windows-NetworkProfile/Operational">*[System[EventID=10000]]</Select></Query></QueryList>'
    }
$triggers += $networkTrigger
$settingsParameters = @{
    StartWhenAvailable = $true
    RunOnlyIfNetworkAvailable = $true
    AllowStartIfOnBatteries = $true
    DontStopIfGoingOnBatteries = $true
    MultipleInstances = 'IgnoreNew'
    ExecutionTimeLimit = (New-TimeSpan -Minutes 10)
    Disable = -not $Enable
}
$settings = New-ScheduledTaskSettingsSet @settingsParameters
$principal = New-ScheduledTaskPrincipal -UserId $userId -LogonType Interactive -RunLevel Limited
$existing = Get-ScheduledTask -TaskName $taskName -ErrorAction SilentlyContinue
if ($existing -and ($existing.Description -cne $description -or
        -not (Test-BackupAutomationTaskOwner $existing.Principal.UserId $userId))) {
    throw '同名の別タスクが存在するため上書きしません。'
}
if ($PSCmdlet.ShouldProcess($taskName, $(if ($Enable) { '自動バックアップを有効化' } else { '無効状態で登録' }))) {
    if (-not (Test-BackupAutomationScriptHost $scriptHost $launcherPath $powerShell)) {
        throw 'Windows Script Hostの非表示ランチャーを実行できないため、既存タスクを変更しません。'
    }
    Register-ScheduledTask -TaskName $taskName -Description $description -Action $action -Trigger $triggers `
        -Settings $settings -Principal $principal -Force | Out-Null
    Write-Host "登録しました: $taskName / Enabled=$([bool]$Enable)"
}
