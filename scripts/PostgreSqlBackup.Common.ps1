function ConvertTo-PgPassValue {
    [CmdletBinding()]
    param(
        [AllowEmptyString()]
        [string]$Value
    )

    return $Value.Replace('\', '\\').Replace(':', '\:')
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
