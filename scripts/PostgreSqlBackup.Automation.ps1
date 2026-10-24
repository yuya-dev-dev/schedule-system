function Assert-BackupAutomationPath([string]$Path) {
    $fullPath = [System.IO.Path]::GetFullPath($Path)
    $separator = [System.IO.Path]::DirectorySeparatorChar
    $repository = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')).TrimEnd($separator) + $separator
    if (($fullPath.TrimEnd($separator) + $separator).StartsWith(
            $repository, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw '自動バックアップの設定・保存先はリポジトリの外にしてください。'
    }
    return $fullPath
}

function Test-BackupAutomationNetwork {
    return [System.Net.NetworkInformation.NetworkInterface]::GetIsNetworkAvailable()
}

function Test-BackupAutomationTaskOwner([string]$RegisteredUser, [string]$ExpectedSid) {
    try {
        $sid = if ($RegisteredUser -match '^S-1-') {
            [System.Security.Principal.SecurityIdentifier]::new($RegisteredUser)
        }
        else {
            ([System.Security.Principal.NTAccount]::new($RegisteredUser)).Translate(
                [System.Security.Principal.SecurityIdentifier])
        }
        return $sid.Value -eq $ExpectedSid
    }
    catch { return $false }
}

function Test-BackupAutomationDocker([switch]$StartDockerDesktop) {
    & docker info --format '{{.ServerVersion}}' 2>$null | Out-Null
    if ($LASTEXITCODE -eq 0) { return $true }
    if (-not $StartDockerDesktop) { return $false }
    & docker desktop start --timeout 90 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { return $false }
    & docker info --format '{{.ServerVersion}}' 2>$null | Out-Null
    return $LASTEXITCODE -eq 0
}

function Invoke-BackupAutomationCommand([string]$ConfigurationPath) {
    & (Join-Path $PSScriptRoot 'Backup-PostgreSql.ps1') -ConfigurationPath $ConfigurationPath -PassThru `
        2>$null 3>$null 4>$null 5>$null 6>$null
}

function Test-BackupAutomationArchive([string]$Directory, [string]$Name, [string]$Hash) {
    if ($Name -notmatch '^schedule-system-[0-9A-Za-z+-]+\.dump$' -or $Hash -notmatch '^[0-9a-fA-F]{64}$') {
        return $false
    }
    $file = Join-Path $Directory $Name
    if (-not (Test-Path -LiteralPath $file -PathType Leaf) -or
            -not (Test-Path -LiteralPath "$file.sha256" -PathType Leaf)) { return $false }
    $metadata = [System.IO.File]::ReadAllText("$file.sha256").Trim()
    return $metadata -ceq "$Hash  $Name" -and (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -eq $Hash
}

function Invoke-DailyPostgreSqlBackup {
    [CmdletBinding()]
    param([Parameter(Mandatory)][string]$ConfigurationPath, [switch]$StartDockerDesktop)

    $configurationFullPath = Assert-BackupAutomationPath $ConfigurationPath
    if (-not (Test-Path -LiteralPath $configurationFullPath -PathType Leaf)) {
        throw 'バックアップの初期設定が未完了です。'
    }
    $configuration = Import-Clixml -LiteralPath $configurationFullPath
    $directory = Assert-BackupAutomationPath $configuration.OutputDirectory
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $lock = $null
    try {
        try {
            $lock = [System.IO.File]::Open((Join-Path $directory '.daily-backup.lock'),
                [System.IO.FileMode]::OpenOrCreate, [System.IO.FileAccess]::ReadWrite, [System.IO.FileShare]::None)
        }
        catch [System.IO.IOException] { return 'Busy' }

        $statePath = Join-Path $directory '.daily-backup-status.json'
        $state = @{}
        if (Test-Path -LiteralPath $statePath -PathType Leaf) {
            try { $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json -AsHashtable }
            catch { $state = @{} }
            if ($state -isnot [System.Collections.IDictionary]) { $state = @{} }
        }
        $configurationHash = (Get-FileHash -LiteralPath $configurationFullPath -Algorithm SHA256).Hash
        $today = (Get-Date).ToString('yyyy-MM-dd')
        if ($state['SuccessDate'] -eq $today -and $state['ConfigurationHash'] -eq $configurationHash -and
                (Test-BackupAutomationArchive $directory $state['BackupName'] $state['Sha256'])) {
            return 'AlreadyCompleted'
        }

        $status = 'Failed'
        try {
            if (-not (Test-BackupAutomationNetwork)) { $status = 'Offline' }
            elseif (-not (Test-BackupAutomationDocker -StartDockerDesktop:$StartDockerDesktop)) { $status = 'DockerUnavailable' }
            else {
                . (Join-Path $PSScriptRoot 'PostgreSqlBackup.Common.ps1')
                $image = Resolve-ApprovedPostgreSqlClientImage $configuration.ClientImage
                # Do not pull a large client image automatically over tethering.
                & docker image inspect $image --format '{{.Id}}' 2>$null | Out-Null
                if ($LASTEXITCODE -ne 0) { throw 'ClientImageUnavailable' }
                Push-Location ([System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')))
                try { $result = Invoke-BackupAutomationCommand $configurationFullPath }
                finally { Pop-Location }
                $name = Split-Path -Leaf $result.BackupFile
                if ([System.IO.Path]::GetFullPath($result.BackupFile) -ne (Join-Path $directory $name) -or
                        -not (Test-BackupAutomationArchive $directory $name $result.Sha256)) {
                    throw 'BackupValidationFailed'
                }
                $state = @{
                    SuccessDate = (Get-Date).ToString('yyyy-MM-dd')
                    ConfigurationHash = $configurationHash
                    BackupName = $name
                    Sha256 = $result.Sha256
                }
                $status = 'Succeeded'
            }
        }
        catch { $status = 'Failed' }
        $state['LastAttemptAt'] = [DateTimeOffset]::Now.ToString('o')
        $state['Status'] = $status
        $temporaryState = "$statePath.$([Guid]::NewGuid().ToString('N')).tmp"
        try {
            [System.IO.File]::WriteAllText($temporaryState, ($state | ConvertTo-Json), [System.Text.UTF8Encoding]::new($false))
            Move-Item -LiteralPath $temporaryState -Destination $statePath -Force
        }
        finally {
            if (Test-Path -LiteralPath $temporaryState) { Remove-Item -LiteralPath $temporaryState -Force }
        }
        return $status
    }
    finally { if ($lock) { $lock.Dispose() } }
}
