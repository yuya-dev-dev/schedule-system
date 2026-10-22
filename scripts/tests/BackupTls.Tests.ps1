[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repositoryRoot = (& git rev-parse --show-toplevel).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Gitリポジトリのルートを取得できません。' }
. (Join-Path $repositoryRoot 'scripts/PostgreSqlBackup.Common.ps1')
$clientImage = Get-ApprovedPostgreSqlClientImage
$executionId = [Guid]::NewGuid().ToString('N')
$testRoot = Join-Path ([System.IO.Path]::GetTempPath()) "schedule-system-backup-tls-$executionId"
$networkName = "schedule-tls-$executionId"
$databaseContainer = "schedule-tls-db-$executionId"
$networkCreated = $false
$databaseStarted = $false
$serverName = 'tls-db.example.invalid'
$wrongServerName = 'wrong-name.example.invalid'
$databasePassword = 'fictional-tls-password'
$powerShellExecutable = (Get-Process -Id $PID).Path
$backupScript = Join-Path $repositoryRoot 'scripts/Backup-PostgreSql.ps1'
$certificates = [System.Collections.Generic.List[System.IDisposable]]::new()

function Invoke-Docker([string[]]$Arguments) {
    & docker @Arguments | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'TLSテスト用のDockerコマンドに失敗しました。' }
}

function New-TestCertificateAuthority([string]$Name) {
    $key = [System.Security.Cryptography.RSA]::Create(2048)
    $certificates.Add($key)
    $request = [System.Security.Cryptography.X509Certificates.CertificateRequest]::new(
        "CN=$Name", $key, [System.Security.Cryptography.HashAlgorithmName]::SHA256,
        [System.Security.Cryptography.RSASignaturePadding]::Pkcs1)
    $request.CertificateExtensions.Add(
        [System.Security.Cryptography.X509Certificates.X509BasicConstraintsExtension]::new($true, $false, 0, $true))
    $request.CertificateExtensions.Add(
        [System.Security.Cryptography.X509Certificates.X509KeyUsageExtension]::new(
            [System.Security.Cryptography.X509Certificates.X509KeyUsageFlags]::KeyCertSign, $true))
    $certificate = $request.CreateSelfSigned([DateTimeOffset]::UtcNow.AddDays(-1), [DateTimeOffset]::UtcNow.AddDays(2))
    $certificates.Add($certificate)
    return $certificate
}

function Write-TestConfiguration([string]$Directory, [string]$HostName) {
    New-Item -ItemType Directory -Path $Directory -Force | Out-Null
    $path = Join-Path $Directory 'backup.clixml'
    [pscustomobject]@{
        JdbcUrl = "jdbc:postgresql://${HostName}:5432/schedule?sslmode=verify-full"
        Username = 'tls_test'
        Password = ConvertTo-SecureString $databasePassword -AsPlainText -Force
        OutputDirectory = Join-Path $Directory 'archives'
        RetentionDays = 14
        ClientImage = $clientImage
    } | Export-Clixml -LiteralPath $path
    return $path
}

function Assert-BackupFailure([string]$Label, [string]$HostName, [string]$CaPath, [string]$ExpectedError) {
    $directory = Join-Path $testRoot $Label
    $configuration = Write-TestConfiguration $directory $HostName
    $archives = Join-Path $directory 'archives'
    New-Item -ItemType Directory -Path $archives | Out-Null
    $oldBackup = Join-Path $archives 'schedule-system-existing.dump'
    [System.IO.File]::WriteAllText($oldBackup, 'fictional existing backup')
    [System.IO.File]::WriteAllText("$oldBackup.sha256", 'fictional existing checksum')
    (Get-Item -LiteralPath $oldBackup).LastWriteTimeUtc = [DateTime]::UtcNow.AddDays(-20)
    $arguments = @('-ConfigurationPath', $configuration, '-AllowInsecureLocalTest',
        '-LocalTestHost', $HostName, '-ClientNetwork', $networkName)
    if ($CaPath) { $arguments += @('-RootCertificatePath', $CaPath) }
    $output = (& $powerShellExecutable -NoProfile -File $backupScript @arguments 2>&1 | Out-String)
    if ($LASTEXITCODE -eq 0 -or $output -notlike "*$ExpectedError*") {
        throw "証明書検証による拒否を確認できません: $Label output=$output"
    }
    if (@(Get-ChildItem -LiteralPath $archives -File).Count -ne 2 -or
            -not (Test-Path -LiteralPath $oldBackup) -or -not (Test-Path -LiteralPath "$oldBackup.sha256")) {
        throw "失敗後に不完全なバックアップが残ったか、既存バックアップが削除されました: $Label"
    }
    Write-Host "TLS拒否を確認: $Label"
}

try {
    New-Item -ItemType Directory -Path $testRoot | Out-Null
    $ca = New-TestCertificateAuthority 'Fictional Backup Test CA'
    $wrongCa = New-TestCertificateAuthority 'Fictional Untrusted CA'
    $caPath = Join-Path $testRoot 'ca.crt'
    $wrongCaPath = Join-Path $testRoot 'wrong-ca.crt'
    [System.IO.File]::WriteAllText($caPath, $ca.ExportCertificatePem())
    [System.IO.File]::WriteAllText($wrongCaPath, $wrongCa.ExportCertificatePem())
    $serverKey = [System.Security.Cryptography.RSA]::Create(2048)
    $certificates.Add($serverKey)
    $request = [System.Security.Cryptography.X509Certificates.CertificateRequest]::new(
        "CN=$serverName", $serverKey, [System.Security.Cryptography.HashAlgorithmName]::SHA256,
        [System.Security.Cryptography.RSASignaturePadding]::Pkcs1)
    $san = [System.Security.Cryptography.X509Certificates.SubjectAlternativeNameBuilder]::new()
    $san.AddDnsName($serverName)
    $request.CertificateExtensions.Add($san.Build())
    $serverCertificate = $request.Create($ca, [DateTimeOffset]::UtcNow.AddHours(-1),
        [DateTimeOffset]::UtcNow.AddDays(1), [byte[]](1..16))
    $certificates.Add($serverCertificate)
    [System.IO.File]::WriteAllText((Join-Path $testRoot 'server.crt'), $serverCertificate.ExportCertificatePem())
    [System.IO.File]::WriteAllText((Join-Path $testRoot 'server.key'), $serverKey.ExportRSAPrivateKeyPem())

    Invoke-Docker @('network', 'create', '--internal', $networkName)
    $networkCreated = $true
    Invoke-Docker @('run', '--detach', '--name', $databaseContainer, '--network', $networkName,
        '--network-alias', $serverName, '--network-alias', $wrongServerName,
        '--tmpfs', '/var/lib/postgresql:rw,noexec,nosuid,size=512m',
        '--mount', "type=bind,source=$testRoot,target=/certs,readonly",
        '--env', 'POSTGRES_USER=tls_test', '--env', "POSTGRES_PASSWORD=$databasePassword",
        '--env', 'POSTGRES_DB=schedule', $clientImage, 'sh', '-c',
        'cp /certs/server.crt /tmp/server.crt && cp /certs/server.key /tmp/server.key && chown postgres:postgres /tmp/server.* && chmod 600 /tmp/server.key && exec docker-entrypoint.sh postgres -c ssl=on -c ssl_cert_file=/tmp/server.crt -c ssl_key_file=/tmp/server.key')
    $databaseStarted = $true
    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        & docker exec $databaseContainer pg_isready --host 127.0.0.1 --username tls_test --dbname schedule 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'TLSテスト用PostgreSQLが起動しませんでした。' }

    $configuration = Write-TestConfiguration (Join-Path $testRoot 'trusted') $serverName
    & $backupScript -ConfigurationPath $configuration -RootCertificatePath $caPath `
        -AllowInsecureLocalTest -LocalTestHost $serverName -ClientNetwork $networkName
    if ($LASTEXITCODE -ne 0) { throw '信頼済みCAと正しいホスト名でバックアップが失敗しました。' }
    $trustedArchives = Join-Path $testRoot 'trusted/archives'
    if (@(Get-ChildItem -LiteralPath $trustedArchives -Filter '*.dump').Count -ne 1 -or
            @(Get-ChildItem -LiteralPath $trustedArchives -Filter '*.sha256').Count -ne 1) {
        throw '信頼済み接続でdumpとSHA-256を作成できませんでした。'
    }
    Write-Host 'TLS成功を確認: 信頼済みCAと正しいホスト名'

    # The same pinned libpq client must support the default system trust store.
    Invoke-Docker @('run', '--rm', '--network', $networkName,
        '--mount', "type=bind,source=$caPath,target=/etc/ssl/cert.pem,readonly",
        '--env', "PGHOST=$serverName", '--env', 'PGUSER=tls_test', '--env', 'PGDATABASE=schedule',
        '--env', "PGPASSWORD=$databasePassword", '--env', 'PGSSLMODE=verify-full',
        '--env', 'PGSSLROOTCERT=system', $clientImage, 'pg_dump', '--schema-only', '--file=/dev/null')
    Write-Host 'TLS成功を確認: システムCAストア'

    Assert-BackupFailure 'untrusted-ca' $serverName $wrongCaPath 'certificate verify failed'
    Assert-BackupFailure 'wrong-hostname' $wrongServerName $caPath 'does not match host name'
    Assert-BackupFailure 'untrusted-system-ca' $serverName '' 'certificate verify failed'
    Write-Host 'バックアップTLSの隔離テストに成功しました。'
}
finally {
    if ($databaseStarted) { & docker rm --force --volumes $databaseContainer | Out-Null }
    if ($networkCreated) { & docker network rm $networkName | Out-Null }
    foreach ($certificate in $certificates) { $certificate.Dispose() }
    $temporaryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd(
        [System.IO.Path]::DirectorySeparatorChar) + [System.IO.Path]::DirectorySeparatorChar
    $testFullPath = [System.IO.Path]::GetFullPath($testRoot)
    if ($testFullPath.StartsWith($temporaryRoot, [System.StringComparison]::OrdinalIgnoreCase) -and
            (Split-Path -Leaf $testFullPath).StartsWith('schedule-system-backup-tls-')) {
        Remove-Item -LiteralPath $testFullPath -Recurse -Force -ErrorAction SilentlyContinue
    }
}
