[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$ConfigurationPath,

    [Parameter(Mandatory)]
    [string]$OutputDirectory,

    [string]$ClientImage = "postgres:17-alpine",

    [switch]$ApprovedStorage
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

if (-not $ApprovedStorage) {
    throw "会社承認済みで、アクセス制限と暗号化が有効な保管先を指定したことを -ApprovedStorage で確認してください。"
}

$configurationFullPath = Get-FullPath $ConfigurationPath
$outputFullPath = Get-FullPath $OutputDirectory
Assert-OutsideRepository $configurationFullPath
Assert-OutsideRepository $outputFullPath

New-Item -ItemType Directory -Force -Path (Split-Path -Parent $configurationFullPath) | Out-Null
New-Item -ItemType Directory -Force -Path $outputFullPath | Out-Null

$jdbcUrl = Read-Host "Neonの直接接続用JDBC URLを入力してください"
if ($jdbcUrl -notmatch "^jdbc:postgresql://") {
    throw "JDBC URLは jdbc:postgresql:// で始まる必要があります。"
}

$credential = Get-Credential -Message "NeonのDBユーザー名とパスワードを入力してください"
if ([string]::IsNullOrWhiteSpace($credential.UserName)) {
    throw "DBユーザー名が空です。"
}

[pscustomobject]@{
    JdbcUrl = $jdbcUrl
    Username = $credential.UserName
    Password = $credential.Password
    OutputDirectory = $outputFullPath
    RetentionDays = 14
    ClientImage = $ClientImage
} | Export-Clixml -LiteralPath $configurationFullPath

& icacls $configurationFullPath /inheritance:r /grant:r "${env:USERNAME}:(F)" | Out-Null
if ($LASTEXITCODE -ne 0) {
    throw "バックアップ設定ファイルのアクセス権を制限できませんでした。"
}

Write-Host "初期設定を保存しました。設定ファイルとバックアップ出力先はGit管理対象外です。"
