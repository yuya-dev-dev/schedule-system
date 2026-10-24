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

function Invoke-BackupAutomationDockerCommand(
    [string[]]$Arguments, [int]$TimeoutSeconds = 15, [switch]$RequireEmptyOutput) {
    $process = [System.Diagnostics.Process]::new()
    $cancellation = [System.Threading.CancellationTokenSource]::new()
    $captured = [System.IO.MemoryStream]::new()
    $clock = [System.Diagnostics.Stopwatch]::StartNew()
    $started = $false
    try {
        $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
        $startInfo.FileName = (Get-Command docker -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
        $startInfo.UseShellExecute = $false
        $startInfo.CreateNoWindow = $true
        $startInfo.RedirectStandardOutput = $true
        $startInfo.RedirectStandardError = $true
        foreach ($argument in $Arguments) { $startInfo.ArgumentList.Add($argument) }
        $process.StartInfo = $startInfo
        $null = $process.Start()
        $started = $true
        $destination = if ($RequireEmptyOutput) { $captured } else { [System.IO.Stream]::Null }
        $output = $process.StandardOutput.BaseStream.CopyToAsync($destination, $cancellation.Token)
        $errors = $process.StandardError.BaseStream.CopyToAsync([System.IO.Stream]::Null, $cancellation.Token)
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) {
            # Desktop starts long-lived children; only stop the timed-out CLI.
            $process.Kill()
            return 124
        }
        # A detached child can keep the pipes open after the CLI process exits.
        $remaining = [math]::Max(0, $TimeoutSeconds * 1000 - [int]$clock.ElapsedMilliseconds)
        if (-not [System.Threading.Tasks.Task]::WhenAll([System.Threading.Tasks.Task[]]@($output, $errors)).Wait($remaining)) {
            return 124
        }
        if ($process.ExitCode -eq 0 -and $RequireEmptyOutput -and $captured.Length -ne 0) { return 125 }
        return $process.ExitCode
    }
    catch { return 1 }
    finally {
        $cancellation.Cancel()
        if ($started) {
            $process.StandardOutput.BaseStream.Dispose()
            $process.StandardError.BaseStream.Dispose()
        }
        $process.Dispose()
        $captured.Dispose()
        $cancellation.Dispose()
    }
}

function Test-BackupAutomationScriptHost([string]$HostPath, [string]$LauncherPath, [string]$PowerShellPath) {
    $process = [System.Diagnostics.Process]::new()
    try {
        $process.StartInfo.FileName = $HostPath
        $process.StartInfo.UseShellExecute = $false
        $process.StartInfo.CreateNoWindow = $true
        foreach ($argument in @('//B', '//NoLogo', $LauncherPath, $PowerShellPath, '--check')) {
            $process.StartInfo.ArgumentList.Add($argument)
        }
        $null = $process.Start()
        if (-not $process.WaitForExit(15000)) {
            $process.Kill($true)
            return $false
        }
        return $process.ExitCode -eq 0
    }
    catch { return $false }
    finally { $process.Dispose() }
}

function Test-BackupAutomationDesktopRunning {
    return $null -ne (Get-Process -Name 'Docker Desktop' -ErrorAction SilentlyContinue | Select-Object -First 1)
}

function Test-BackupAutomationDocker([switch]$StartDockerDesktop, [ref]$StartedByBackup) {
    if ($StartedByBackup) { $StartedByBackup.Value = $false }
    $probe = @('info', '--format', '{{.ServerVersion}}')
    if ((Invoke-BackupAutomationDockerCommand $probe) -eq 0) { return $true }
    if (-not $StartDockerDesktop) { return $false }
    $wasRunning = Test-BackupAutomationDesktopRunning
    $startExit = Invoke-BackupAutomationDockerCommand @('desktop', 'start', '--timeout', '120') -TimeoutSeconds 130
    # Desktop can report completion before the engine accepts requests.
    for ($attempt = 0; $attempt -lt 6; $attempt++) {
        if ((Invoke-BackupAutomationDockerCommand $probe) -eq 0) {
            if ($StartedByBackup) { $StartedByBackup.Value = -not $wasRunning -and $startExit -eq 0 }
            return $true
        }
        if ($attempt -lt 5) { Start-Sleep -Seconds 2 }
    }
    return $false
}

function Stop-BackupAutomationOwnedDocker {
    # A failed inventory check must never be interpreted as an empty engine.
    $inventory = Invoke-BackupAutomationDockerCommand @('ps', '--quiet') -RequireEmptyOutput
    if ($inventory -ne 0) { return 'KeptRunning' }
    $exitCode = Invoke-BackupAutomationDockerCommand @('desktop', 'stop', '--timeout', '60') -TimeoutSeconds 70
    return $(if ($exitCode -eq 0) { 'Stopped' } else { 'StopFailed' })
}

function Save-BackupAutomationState([string]$StatePath, [System.Collections.IDictionary]$State) {
    $temporaryState = "$StatePath.$([Guid]::NewGuid().ToString('N')).tmp"
    try {
        [System.IO.File]::WriteAllText($temporaryState, ($State | ConvertTo-Json), [System.Text.UTF8Encoding]::new($false))
        Move-Item -LiteralPath $temporaryState -Destination $StatePath -Force
    }
    finally {
        if (Test-Path -LiteralPath $temporaryState) { Remove-Item -LiteralPath $temporaryState -Force }
    }
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
        $failureCode = $null
        $startedDocker = $false
        try {
            if (-not (Test-BackupAutomationNetwork)) { $status = 'Offline' }
            elseif (-not (Test-BackupAutomationDocker -StartDockerDesktop:$StartDockerDesktop -StartedByBackup ([ref]$startedDocker))) {
                $status = 'DockerUnavailable'
                $failureCode = 'DockerNotReady'
            }
            else {
                . (Join-Path $PSScriptRoot 'PostgreSqlBackup.Common.ps1')
                $failureCode = 'ClientImageUnavailable'
                $image = Resolve-ApprovedPostgreSqlClientImage $configuration.ClientImage
                # Do not pull a large client image automatically over tethering.
                if ((Invoke-BackupAutomationDockerCommand @('image', 'inspect', $image, '--format', '{{.Id}}')) -ne 0) {
                    throw 'ClientImageUnavailable'
                }
                $failureCode = 'BackupFailed'
                Push-Location ([System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..')))
                try { $result = Invoke-BackupAutomationCommand $configurationFullPath }
                finally { Pop-Location }
                $failureCode = 'ArchiveValidationFailed'
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
                    Status = 'Succeeded'
                    FailureCode = $null
                    LastAttemptAt = [DateTimeOffset]::Now.ToString('o')
                    DockerCleanupStatus = $(if ($startedDocker) { 'Pending' } else { 'NotStartedByBackup' })
                }
                Save-BackupAutomationState $statePath $state
                $status = 'Succeeded'
                $failureCode = $null
                if ($startedDocker) {
                    try { $state['DockerCleanupStatus'] = Stop-BackupAutomationOwnedDocker }
                    catch { $state['DockerCleanupStatus'] = 'StopFailed' }
                }
            }
        }
        catch { $status = 'Failed' }
        $state['LastAttemptAt'] = [DateTimeOffset]::Now.ToString('o')
        $state['Status'] = $status
        $state['FailureCode'] = $failureCode
        Save-BackupAutomationState $statePath $state
        return $status
    }
    finally { if ($lock) { $lock.Dispose() } }
}
