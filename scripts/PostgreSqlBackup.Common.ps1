function ConvertTo-PgPassValue {
    [CmdletBinding()]
    param(
        [AllowEmptyString()]
        [string]$Value
    )

    return $Value.Replace('\', '\\').Replace(':', '\:')
}

function ConvertFrom-BackupJdbcUrl {
    [CmdletBinding()]
    param(
        [string]$JdbcUrl,
        [switch]$AllowInsecureLocalTest,
        [string]$LocalTestHost
    )

    if ($JdbcUrl -notmatch '^jdbc:postgresql://') {
        throw "JDBC URLは jdbc:postgresql:// で始まる必要があります。"
    }
    try {
        $uri = [System.Uri]($JdbcUrl.Substring(5))
    }
    catch {
        throw "JDBC URLの形式が不正です。"
    }
    if (-not [string]::IsNullOrWhiteSpace($uri.UserInfo)) {
        throw "JDBC URLに認証情報を含めないでください。"
    }
    if ($uri.Host -match 'pooler') {
        throw "バックアップにはNeonの直接接続用ホストを指定してください。プーラー接続は使用できません。"
    }
    $database = $uri.AbsolutePath.Trim('/')
    if ([string]::IsNullOrWhiteSpace($uri.Host) -or
            [string]::IsNullOrWhiteSpace($database) -or $uri.Fragment) {
        throw "JDBC URLのホスト、データベース名、または形式が不正です。"
    }

    $query = [System.Web.HttpUtility]::ParseQueryString($uri.Query)
    foreach ($key in $query.AllKeys) {
        if ($null -eq $key) {
            throw "JDBC URLのオプションは空でない値を1つだけ指定してください。"
        }
        if ($null -ne $key -and $key -match '^(ssl|channelbinding)' -and $key -ine 'sslmode') {
            throw "JDBC URLのTLSオプションはsslmodeだけを指定してください。CAはRootCertificatePathで指定します。"
        }
    }
    $sslMode = 'verify-full'
    $sslValues = $query.GetValues('sslmode')
    if ($null -ne $sslValues) {
        if ($sslValues.Count -ne 1 -or [string]::IsNullOrWhiteSpace($sslValues[0])) {
            throw "JDBC URLのsslmodeは空でない値を1つだけ指定してください。"
        }
        $sslMode = $sslValues[0].ToLowerInvariant()
    }
    if ($sslMode -notin 'disable', 'allow', 'prefer', 'require', 'verify-ca', 'verify-full') {
        throw "JDBC URLのsslmodeが不正です。"
    }
    $hostName = $uri.Host.Trim('[', ']')
    if ($sslMode -ne 'verify-full') {
        $localHosts = 'localhost', '127.0.0.1', '::1', 'host.docker.internal'
        $matchesExplicitTestHost = -not [string]::IsNullOrWhiteSpace($LocalTestHost) -and
            $hostName.Equals($LocalTestHost.Trim('[', ']'), [System.StringComparison]::OrdinalIgnoreCase)
        if (-not $AllowInsecureLocalTest -or
                ($hostName -notin $localHosts -and -not $matchesExplicitTestHost)) {
            throw "バックアップ接続には sslmode=verify-full を指定してください。既存設定は再設定してください。"
        }
    }

    return [pscustomobject]@{
        Host = $hostName
        Port = if ($uri.Port -gt 0) { $uri.Port } else { 5432 }
        Database = $database
        SslMode = $sslMode
    }
}

function Get-ApprovedPostgreSqlClientImage {
    return "docker.io/library/postgres:18-alpine@sha256:d3e1620b530c944afa6e887d22eb899824da68e19c52024bf98f5220c88a65b2"
}

function Resolve-ApprovedPostgreSqlClientImage {
    [CmdletBinding()]
    param(
        [AllowNull()]
        [AllowEmptyString()]
        [string]$ClientImage
    )

    $approvedImage = Get-ApprovedPostgreSqlClientImage
    if ([string]::IsNullOrWhiteSpace($ClientImage)) {
        return $approvedImage
    }
    if (-not $ClientImage.Equals($approvedImage, [System.StringComparison]::Ordinal)) {
        throw "PostgreSQLクライアントには検証済みの固定イメージだけを使用できます。"
    }
    return $approvedImage
}
