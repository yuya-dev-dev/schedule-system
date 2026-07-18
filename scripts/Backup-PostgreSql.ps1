[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$ConfigurationPath,

    [switch]$AllowInsecureLocalTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

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

function Convert-JdbcUrl([string]$JdbcUrl) {
    if ($JdbcUrl -notmatch "^jdbc:postgresql://") {
        throw "JDBC URLは jdbc:postgresql:// で始まる必要があります。"
    }

    $uri = [System.Uri]($JdbcUrl.Substring(5))
    if (-not [string]::IsNullOrWhiteSpace($uri.UserInfo)) {
        throw "JDBC URLに認証情報を含めないでください。"
    }
    if ($uri.Host -match "pooler") {
        throw "バックアップにはNeonの直接接続用ホストを指定してください。プーラー接続は使用できません。"
    }

    $database = $uri.AbsolutePath.Trim("/")
    if ([string]::IsNullOrWhiteSpace($database)) {
        throw "JDBC URLにデータベース名がありません。"
    }

    $sslMode = "require"
    if ($uri.Query -match "(?:^|[?&])sslmode=([^&]+)") {
        $sslMode = [System.Uri]::UnescapeDataString($Matches[1])
    }
    if ($sslMode -notin "disable", "allow", "prefer", "require", "verify-ca", "verify-full") {
        throw "JDBC URLのsslmodeが不正です。"
    }
    if ($sslMode -notin "require", "verify-ca", "verify-full") {
        $localHosts = "localhost", "127.0.0.1", "::1", "host.docker.internal"
        if (-not $AllowInsecureLocalTest -or $uri.Host -notin $localHosts) {
            throw "バックアップ接続には sslmode=require 以上を指定してください。"
        }
    }

    return [pscustomobject]@{
        Host = $uri.Host
        Port = if ($uri.Port -gt 0) { $uri.Port } else { 5432 }
        Database = $database
        SslMode = $sslMode
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

$connection = Convert-JdbcUrl $backupConfiguration.JdbcUrl
$outputDirectory = Get-FullPath $backupConfiguration.OutputDirectory
Assert-OutsideRepository $outputDirectory
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null

$plainPassword = $null
$backupFilePath = $null
$backupHashPath = $null
try {
    $plainPassword = [System.Net.NetworkCredential]::new("", $backupConfiguration.Password).Password
    $escapedPassword = $plainPassword.Replace("\\", "\\\\").Replace(":", "\\:")
    $pgPassLine = "{0}:{1}:{2}:{3}:{4}" -f $connection.Host, $connection.Port, $connection.Database, $backupConfiguration.Username, $escapedPassword

    $timestamp = [DateTimeOffset]::Now.ToString("yyyyMMdd-HHmmsszzz").Replace(":", "")
    $executionId = [Guid]::NewGuid().ToString("N")
    $backupFileName = "schedule-system-$timestamp-$executionId.dump"
    $backupFilePath = Join-Path $outputDirectory $backupFileName
    $backupMount = "type=bind,source=$outputDirectory,target=/backup"
    $dockerArguments = @(
        "run", "--rm", "--interactive",
        "--mount", $backupMount,
        "--env", "PGHOST=$($connection.Host)",
        "--env", "PGPORT=$($connection.Port)",
        "--env", "PGUSER=$($backupConfiguration.Username)",
        "--env", "PGDATABASE=$($connection.Database)",
        "--env", "PGSSLMODE=$($connection.SslMode)",
        $backupConfiguration.ClientImage,
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

    & docker run --rm --mount $backupMount $backupConfiguration.ClientImage pg_restore --list "/backup/$backupFileName" | Out-Null
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
