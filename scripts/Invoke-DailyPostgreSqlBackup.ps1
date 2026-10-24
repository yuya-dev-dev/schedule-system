#Requires -Version 7.0
[CmdletBinding()]
param(
    [string]$ConfigurationPath = (Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'ScheduleSystem/backup-config.clixml'),
    [switch]$StartDockerDesktop
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'PostgreSqlBackup.Automation.ps1')
try {
    $status = Invoke-DailyPostgreSqlBackup -ConfigurationPath $ConfigurationPath -StartDockerDesktop:$StartDockerDesktop
    Write-Host "Backup automation: $status"
    if ($status -in 'Failed', 'DockerUnavailable') { exit 1 }
}
catch {
    Write-Host 'Backup automation: Failed. 初期設定とローカル保存先を確認してください。'
    exit 1
}
