[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$ConfigurationPath,

    [switch]$AllowInsecureLocalTest,

    [string]$LocalTestHost,

    [string]$ClientNetwork,

    [string]$RootCertificatePath,

    [switch]$PassThru
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "PostgreSqlBackup.Common.ps1")

if ((-not [string]::IsNullOrWhiteSpace($LocalTestHost) -or
        -not [string]::IsNullOrWhiteSpace($ClientNetwork)) -and
        -not $AllowInsecureLocalTest) {
    throw "ローカルテスト用の接続指定には AllowInsecureLocalTest が必要です。"
}

function Get-FullPath([string]$Path) {
    return [System.IO.Path]::GetFullPath($Path)
}

function Assert-OutsideRepository([string]$Path) {
    $repositoryRoot = (& git rev-parse --show-toplevel).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw "Gitリポジトリのルートを取得できません。リポジトリ内で実行してください。"
    }

    $separator = [System.IO.Path]::DirectorySeparatorChar
    $repositoryRoot = (Get-FullPath $repositoryRoot).TrimEnd($separator) + $separator
    $candidatePath = (Get-FullPath $Path).TrimEnd($separator) + $separator
    if ($candidatePath.StartsWith($repositoryRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "バックアップ設定と出力先はリポジトリの外にしてください。"
    }
}

$configurationFullPath = Get-FullPath $ConfigurationPath
Assert-OutsideRepository $configurationFullPath
if (-not (Test-Path -LiteralPath $configurationFullPath -PathType Leaf)) {
    throw "バックアップ設定ファイルが見つかりません。"
}

$backupConfiguration = Import-Clixml -LiteralPath $configurationFullPath
$requiredProperties = "JdbcUrl", "Username", "Password", "OutputDirectory", "RetentionDays", "ClientImage"
foreach ($property in $requiredProperties) {
    if ($null -eq $backupConfiguration.$property -or [string]::IsNullOrWhiteSpace([string]$backupConfiguration.$property)) {
        throw "バックアップ設定に $property がありません。"
    }
}
if ($backupConfiguration.Password -isnot [System.Security.SecureString]) {
    throw "バックアップ設定のパスワード形式が不正です。"
}
if ([int]$backupConfiguration.RetentionDays -ne 14) {
    throw "バックアップ保持期間は14日固定です。"
}
$clientImage = Resolve-ApprovedPostgreSqlClientImage $backupConfiguration.ClientImage

$connection = ConvertFrom-BackupJdbcUrl $backupConfiguration.JdbcUrl `
    -AllowInsecureLocalTest:$AllowInsecureLocalTest -LocalTestHost $LocalTestHost
$rootCertificate = $null
if (-not [string]::IsNullOrWhiteSpace($RootCertificatePath)) {
    if ($connection.SslMode -ne 'verify-full') {
        throw "RootCertificatePathはsslmode=verify-fullの場合だけ指定できます。"
    }
    $rootCertificate = Get-FullPath $RootCertificatePath
    if (-not (Test-Path -LiteralPath $rootCertificate -PathType Leaf) -or $rootCertificate.Contains(',')) {
        throw "RootCertificatePathには存在するCAファイルを指定してください。パスにカンマは使用できません。"
    }
}
$outputDirectory = Get-FullPath $backupConfiguration.OutputDirectory
Assert-OutsideRepository $outputDirectory
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

$plainPassword = $null
$backupFilePath = $null
$backupHashPath = $null
try {
    $plainPassword = [System.Net.NetworkCredential]::new("", $backupConfiguration.Password).Password
    $escapedPassword = ConvertTo-PgPassValue $plainPassword
    $pgPassLine = "{0}:{1}:{2}:{3}:{4}" -f $connection.Host, $connection.Port, $connection.Database, $backupConfiguration.Username, $escapedPassword

    $timestamp = [DateTimeOffset]::Now.ToString("yyyyMMdd-HHmmsszzz").Replace(":", "")
    $executionId = [Guid]::NewGuid().ToString("N")
    $backupFileName = "schedule-system-$timestamp-$executionId.dump"
    $backupFilePath = Join-Path $outputDirectory $backupFileName
    $backupMount = "type=bind,source=$outputDirectory,target=/backup"
    $dockerArguments = @("run", "--rm", "--interactive")
    $runningOnWindows = [System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform(
        [System.Runtime.InteropServices.OSPlatform]::Windows)
    if (-not $runningOnWindows) {
        $hostUserId = (& id -u | Out-String).Trim()
        $hostGroupId = (& id -g | Out-String).Trim()
        if ($LASTEXITCODE -ne 0 -or -not $hostUserId -or -not $hostGroupId) {
            throw "Dockerコンテナへ渡すホストのUID/GIDを取得できません。"
        }
        $dockerArguments += @("--user", ("{0}:{1}" -f $hostUserId, $hostGroupId))
    }
    if (-not [string]::IsNullOrWhiteSpace($ClientNetwork)) {
        $dockerArguments += @("--network", $ClientNetwork)
    }
    if ($connection.SslMode -eq 'verify-full') {
        $rootCertificateInContainer = 'system'
        if ($rootCertificate) {
            $dockerArguments += @('--mount', "type=bind,source=$rootCertificate,target=/run/backup-root.crt,readonly")
            $rootCertificateInContainer = '/run/backup-root.crt'
        }
        $dockerArguments += @('--env', "PGSSLROOTCERT=$rootCertificateInContainer")
    }
    $dockerArguments += @(
        "--mount", $backupMount,
        "--env", "PGHOST=$($connection.Host)",
        "--env", "PGPORT=$($connection.Port)",
        "--env", "PGUSER=$($backupConfiguration.Username)",
        "--env", "PGDATABASE=$($connection.Database)",
        "--env", "PGSSLMODE=$($connection.SslMode)",
        $clientImage,
        "sh", "-c",
        "umask 077 && cat > /tmp/.pgpass && chmod 600 /tmp/.pgpass && PGPASSFILE=/tmp/.pgpass exec pg_dump --format=custom --no-owner --no-acl --file /backup/$backupFileName"
    )
    $pgPassLine | & docker @dockerArguments
    if ($LASTEXITCODE -ne 0) {
        throw "pg_dumpに失敗しました。出力先のバックアップは使用しないでください。"
    }

    if (-not (Test-Path -LiteralPath $backupFilePath -PathType Leaf)) {
        throw "pg_dumpは成功しましたが、バックアップファイルが見つかりません。"
    }

    & docker run --rm --mount $backupMount $clientImage pg_restore --list "/backup/$backupFileName" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "作成したバックアップをpg_restoreで読み取れません。"
    }

    $hash = (Get-FileHash -LiteralPath $backupFilePath -Algorithm SHA256).Hash
    $backupHashPath = "$backupFilePath.sha256"
    [System.IO.File]::WriteAllText($backupHashPath, "$hash  $backupFileName$([Environment]::NewLine)", [System.Text.UTF8Encoding]::new($false))

    $expiration = [DateTime]::UtcNow.AddDays(-[int]$backupConfiguration.RetentionDays)
    Get-ChildItem -LiteralPath $outputDirectory -Filter "schedule-system-*.dump" -File |
        Where-Object { $_.LastWriteTimeUtc -lt $expiration } |
        ForEach-Object {
            Remove-Item -LiteralPath $_.FullName
            $hashFile = "$($_.FullName).sha256"
            if (Test-Path -LiteralPath $hashFile -PathType Leaf) {
                Remove-Item -LiteralPath $hashFile
            }
        }

    Write-Host "バックアップを作成しました: $backupFileName"
    if ($PassThru) {
        [pscustomobject]@{ BackupFile = $backupFilePath; Sha256 = $hash }
    }
}
catch {
    if ($backupHashPath -and (Test-Path -LiteralPath $backupHashPath -PathType Leaf)) {
        Remove-Item -LiteralPath $backupHashPath -Force
    }
    if ($backupFilePath -and (Test-Path -LiteralPath $backupFilePath -PathType Leaf)) {
        Remove-Item -LiteralPath $backupFilePath -Force
    }
    throw
}
finally {
    $plainPassword = $null
}
