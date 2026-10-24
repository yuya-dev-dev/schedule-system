#Requires -Version 7.0
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../PostgreSqlBackup.Automation.ps1')
$dockerProcessCommand = (Get-Item Function:Invoke-BackupAutomationDockerCommand).ScriptBlock
$scenario = [pscustomobject]@{ ReadyAfter = 0; Probes = 0; Starts = 0; Sleeps = 0; StartExit = 0; DesktopRunning = $false; InventoryExit = 0; StopExit = 0; Stops = 0 }
function Assert-Equal($Expected, $Actual, [string]$Label) {
    if ($Expected -cne $Actual) { throw "$Label expected=$Expected actual=$Actual" }
}
function Test-BackupAutomationDesktopRunning { return $scenario.DesktopRunning }
function Invoke-BackupAutomationDockerCommand([string[]]$Arguments, [int]$TimeoutSeconds = 15, [switch]$RequireEmptyOutput) {
    if ($Arguments[0] -eq 'info') {
        $scenario.Probes++
        return $(if ($scenario.Probes -gt $scenario.ReadyAfter) { 0 } else { 1 })
    }
    if (($Arguments -join ' ') -eq 'ps --quiet' -and $RequireEmptyOutput) { return $scenario.InventoryExit }
    if (($Arguments -join ' ') -eq 'desktop stop --timeout 60' -and $TimeoutSeconds -eq 70) {
        $scenario.Stops++
        return $scenario.StopExit
    }
    if (($Arguments -join ' ') -ne 'desktop start --timeout 120' -or $TimeoutSeconds -ne 130) {
        throw '想定外のDockerコマンドです。'
    }
    $scenario.Starts++
    return $scenario.StartExit
}
function Start-Sleep { param([int]$Seconds) $scenario.Sleeps++ }

Assert-Equal $true (Test-BackupAutomationDocker -StartDockerDesktop) '稼働済みエンジン'
Assert-Equal 0 $scenario.Starts '稼働済みなら起動・再起動しない'
$scenario.ReadyAfter = 3
$scenario.Probes = 0
Assert-Equal $false (Test-BackupAutomationDocker) '自動起動なし'
Assert-Equal 0 $scenario.Starts '自動起動を指定しなければ起動しない'
$scenario.Probes = 0
$owned = $false
Assert-Equal $true (Test-BackupAutomationDocker -StartDockerDesktop -StartedByBackup ([ref]$owned)) '起動後の準備待ち'
Assert-Equal $true $owned '処理自身が起動したDockerを識別'
Assert-Equal 1 $scenario.Starts 'Docker起動は一度だけ'
Assert-Equal 2 $scenario.Sleeps 'エンジン準備中は待機'
$scenario.Probes = 0
$scenario.ReadyAfter = 1
$scenario.StartExit = 1
Assert-Equal $true (Test-BackupAutomationDocker -StartDockerDesktop) '起動CLIが失敗しても稼働を実測する'
$scenario.StartExit = 0
$scenario.DesktopRunning = $true
$scenario.Probes = 0
Assert-Equal $true (Test-BackupAutomationDocker -StartDockerDesktop -StartedByBackup ([ref]$owned)) '元から起動中のDesktopの準備待ち'
Assert-Equal $false $owned '既存Desktopの所有権を取得しない'
$scenario.Probes = 0
$scenario.ReadyAfter = 100
Assert-Equal $false (Test-BackupAutomationDocker -StartDockerDesktop) '起動できない場合は有限回で終了'
Assert-Equal 7 $scenario.Probes '無限に再試行しない'
$scenario.InventoryExit = 125
Assert-Equal 'KeptRunning' (Stop-BackupAutomationOwnedDocker) '稼働コンテナがあれば停止しない'
$scenario.InventoryExit = 1
Assert-Equal 'KeptRunning' (Stop-BackupAutomationOwnedDocker) '稼働状況不明でも停止しない'
Assert-Equal 0 $scenario.Stops '危険な停止を呼ばない'
$scenario.InventoryExit = 0
Assert-Equal 'Stopped' (Stop-BackupAutomationOwnedDocker) '空のエンジンを停止'
$scenario.StopExit = 1
Assert-Equal 'StopFailed' (Stop-BackupAutomationOwnedDocker) '停止失敗を区別'

# Docker Desktop installs both docker.exe and an extensionless docker script.
$processTestRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('schedule-system-pipe-tests-' + [Guid]::NewGuid().ToString('N'))
$childPidPath = $null
function Get-Command {
    param([string]$Name, [string]$CommandType, [string]$ErrorAction)
    @([pscustomobject]@{ Source = (Get-Process -Id $PID).Path },
        [pscustomobject]@{ Source = 'second-match-must-not-be-concatenated' })
}
try {
    Assert-Equal 0 (& $dockerProcessCommand @('-NoProfile', '-Command', 'exit 0')) '実行ファイルは先頭の一致だけを使用'
    Assert-Equal 29 (& $dockerProcessCommand @('-NoProfile', '-Command', 'exit 29')) 'ネイティブ終了コードを保持'
    Assert-Equal 124 (& $dockerProcessCommand @('-NoProfile', '-Command', 'Start-Sleep -Seconds 30') -TimeoutSeconds 1) 'コマンドの待機を制限'
    Assert-Equal 125 (& $dockerProcessCommand @('-NoProfile', '-Command', 'Write-Output fictional-container') -RequireEmptyOutput) '出力があれば空と判定しない'
    New-Item -ItemType Directory -Path $processTestRoot | Out-Null
    $childPidPath = Join-Path $processTestRoot 'child.pid'
    $parentPath = Join-Path $processTestRoot 'parent.ps1'
    $parentFixture = @'
param([string]$PidPath)
$startInfo = [System.Diagnostics.ProcessStartInfo]::new()
$startInfo.FileName = (Get-Process -Id $PID).Path
$startInfo.UseShellExecute = $false
$startInfo.CreateNoWindow = $true
foreach ($argument in @('-NoProfile', '-Command', 'Start-Sleep -Seconds 30')) {
    $startInfo.ArgumentList.Add($argument)
}
$child = [System.Diagnostics.Process]::Start($startInfo)
[System.IO.File]::WriteAllText($PidPath, [string]$child.Id)
exit 0
'@
    [System.IO.File]::WriteAllText($parentPath, $parentFixture)
    $clock = [System.Diagnostics.Stopwatch]::StartNew()
    Assert-Equal 124 (& $dockerProcessCommand @('-NoProfile', '-File', $parentPath, '-PidPath', $childPidPath) -TimeoutSeconds 2) '親終了後も子が保持する出力パイプの期限'
    if ($clock.Elapsed.TotalSeconds -gt 8) { throw '出力パイプの待機が期限を超えました。' }
}
finally {
    Remove-Item Function:Get-Command
    if ($childPidPath -and (Test-Path -LiteralPath $childPidPath)) {
        $childId = [int][System.IO.File]::ReadAllText($childPidPath)
        $child = Get-Process -Id $childId -ErrorAction SilentlyContinue
        if ($child -and $child.Path -eq (Get-Process -Id $PID).Path) {
            $child.Kill($true)
            $null = $child.WaitForExit(5000)
        }
    }
    $temporaryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    $fullPath = [System.IO.Path]::GetFullPath($processTestRoot)
    if ($fullPath.StartsWith($temporaryRoot, [System.StringComparison]::OrdinalIgnoreCase) -and
            (Split-Path -Leaf $fullPath).StartsWith('schedule-system-pipe-tests-')) {
        Remove-Item -LiteralPath $fullPath -Recurse -Force -ErrorAction SilentlyContinue
    }
}

if ($IsWindows) {
    $testRoot = Join-Path ([System.IO.Path]::GetTempPath()) ('schedule-system-hidden-tests-' + [Guid]::NewGuid().ToString('N'))
    try {
        New-Item -ItemType Directory -Path $testRoot | Out-Null
        $launcher = Join-Path $testRoot 'Run-PostgreSqlBackupHidden.js'
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot '../Run-PostgreSqlBackupHidden.js') -Destination $launcher
        $configurationPath = Join-Path $testRoot 'input file 日本語.txt'
        [System.IO.File]::WriteAllText($configurationPath, '0')
        $fixture = @'
param([string]$ConfigurationPath, [switch]$StartDockerDesktop)
$ErrorActionPreference = 'Stop'
Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
public static class BackupConsoleVisibility {
    [DllImport("kernel32.dll")] public static extern IntPtr GetConsoleWindow();
    [DllImport("user32.dll")] public static extern bool IsWindowVisible(IntPtr window);
}
"@
$visible = [BackupConsoleVisibility]::IsWindowVisible([BackupConsoleVisibility]::GetConsoleWindow())
[System.IO.File]::WriteAllText("$ConfigurationPath.result.json", (@{
    Visible = $visible; ConfigurationPath = $ConfigurationPath; StartDockerDesktop = [bool]$StartDockerDesktop
} | ConvertTo-Json))
exit [int][System.IO.File]::ReadAllText($ConfigurationPath)
'@
        [System.IO.File]::WriteAllText((Join-Path $testRoot 'Invoke-DailyPostgreSqlBackup.ps1'), $fixture)
        function Invoke-TestHiddenLauncher([string[]]$LauncherArguments) {
            $process = [System.Diagnostics.Process]::new()
            try {
                $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
                $startInfo.FileName = Join-Path $env:WINDIR 'System32/wscript.exe'
                $startInfo.UseShellExecute = $false
                $startInfo.CreateNoWindow = $true
                foreach ($argument in @('//B', '//NoLogo', $launcher) + $LauncherArguments) {
                    $startInfo.ArgumentList.Add($argument)
                }
                $process.StartInfo = $startInfo
                $null = $process.Start()
                if (-not $process.WaitForExit(60000)) {
                    $process.Kill($true)
                    throw '非表示ランチャーのテストがタイムアウトしました。'
                }
                return $process.ExitCode
            }
            finally { $process.Dispose() }
        }
        $powerShell = (Get-Process -Id $PID).Path
        Assert-Equal $true (Test-BackupAutomationScriptHost (Join-Path $env:WINDIR 'System32/wscript.exe') $launcher $powerShell) '登録前にScript Hostを実行確認'
        Assert-Equal $false (Test-BackupAutomationScriptHost (Join-Path $testRoot 'missing-host.exe') $launcher $powerShell) '起動できないHostは拒否'
        Assert-Equal 0 (Invoke-TestHiddenLauncher @($powerShell, $configurationPath)) '非表示起動の成功'
        $result = Get-Content -LiteralPath "$configurationPath.result.json" -Raw | ConvertFrom-Json
        Assert-Equal $false $result.Visible '子PowerShellのコンソールを非表示'
        Assert-Equal $configurationPath $result.ConfigurationPath '空白・日本語を含むパスの引継ぎ'
        Assert-Equal $true $result.StartDockerDesktop '自動起動スイッチの引継ぎ'
        [System.IO.File]::WriteAllText($configurationPath, '37')
        Assert-Equal 37 (Invoke-TestHiddenLauncher @($powerShell, $configurationPath)) '子プロセスの失敗を隠さない'
        Assert-Equal 1 (Invoke-TestHiddenLauncher @()) '引数不足は非表示で失敗'
    }
    finally {
        $temporaryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd(
            [System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
        $fullPath = [System.IO.Path]::GetFullPath($testRoot)
        if ($fullPath.StartsWith($temporaryRoot, [System.StringComparison]::OrdinalIgnoreCase) -and
                (Split-Path -Leaf $fullPath).StartsWith('schedule-system-hidden-tests-')) {
            Remove-Item -LiteralPath $fullPath -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
}
Write-Host 'Docker準備待ち・非表示起動の対象限定テストに成功しました。本番接続・タスク登録は行っていません。'
