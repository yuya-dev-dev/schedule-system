function ConvertTo-PgPassValue {
    [CmdletBinding()]
    param(
        [AllowEmptyString()]
        [string]$Value
    )

    return $Value.Replace('\', '\\').Replace(':', '\:')
}
