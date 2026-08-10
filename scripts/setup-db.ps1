[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "common.ps1")

$root = Get-ProjectRoot
Import-ProjectEnv -Path (Join-Path $root ".env")

$database = Get-DatabaseConnection
$dbHost = $database.Host
$dbPort = $database.Port
$dbName = $database.Name
$sslMode = $database.SslMode
$dbUser = Get-RequiredEnvValue -Name "DB_USERNAME"
$dbPassword = Get-RequiredEnvValue -Name "DB_PASSWORD"
$dbSslCa = Get-EnvValue -Name "DB_SSL_CA"

if ($dbPassword -in @("change-me", "replace-with-a-local-database-password")) {
    throw "DB_PASSWORD is still the example value. Edit the root .env first."
}
if ($dbName -notmatch '^[A-Za-z0-9_]+$') {
    throw "DB_NAME may only contain ASCII letters, digits, and underscores."
}

$mysql = Find-Executable -Names @("mysql.exe", "mysql") -FallbackPatterns @(
    "C:\Program Files\MySQL\MySQL Server *\bin\mysql.exe",
    "C:\Program Files\MariaDB *\bin\mysql.exe",
    "C:\xampp\mysql\bin\mysql.exe"
)
if ($null -eq $mysql) {
    throw "Cannot find the mysql client. Install MySQL 8 and add its bin directory to PATH."
}

if ($dbUser.Contains("`n") -or $dbUser.Contains("`r") -or $dbPassword.Contains("`n") -or $dbPassword.Contains("`r")) {
    throw "The database username and password cannot contain newlines."
}

function ConvertTo-MySqlOptionValue {
    param([Parameter(Mandatory = $true)][string]$Value)
    return '"' + $Value.Replace('\', '\\').Replace('"', '\"') + '"'
}

$defaultsFile = Join-Path ([System.IO.Path]::GetTempPath()) ("love-space-mysql-{0}.cnf" -f [Guid]::NewGuid().ToString("N"))
try {
    $options = @(
        "[client]",
        "host=$(ConvertTo-MySqlOptionValue $dbHost)",
        "port=$dbPort",
        "user=$(ConvertTo-MySqlOptionValue $dbUser)",
        "password=$(ConvertTo-MySqlOptionValue $dbPassword)",
        "ssl-mode=$sslMode",
        "default-character-set=utf8mb4"
    ) -join [Environment]::NewLine
    if (-not [string]::IsNullOrWhiteSpace($dbSslCa)) {
        $options += [Environment]::NewLine + "ssl-ca=$(ConvertTo-MySqlOptionValue $dbSslCa)"
    }
    [System.IO.File]::WriteAllText($defaultsFile, $options, [System.Text.UTF8Encoding]::new($false))

    $sql = "CREATE DATABASE IF NOT EXISTS ``$dbName`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; ALTER DATABASE ``$dbName`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci; SELECT SCHEMA_NAME, DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '$dbName';"
    Write-Host "Checking MySQL and creating database '$dbName' when needed..." -ForegroundColor Cyan
    & $mysql "--defaults-extra-file=$defaultsFile" --connect-timeout=10 --execute=$sql
    Assert-LastExitCode -Action "Database setup"

    Write-Host "Database '$dbName' is ready with the utf8mb4 character set." -ForegroundColor Green
}
finally {
    if (Test-Path -LiteralPath $defaultsFile) {
        Remove-Item -LiteralPath $defaultsFile -Force -ErrorAction SilentlyContinue
    }
}
