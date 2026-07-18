[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "common.ps1")

$root = Get-ProjectRoot
Import-ProjectEnv -Path (Join-Path $root ".env")

$dbHost = Get-EnvValue -Name "DB_HOST" -Default "localhost"
$dbPortText = Get-EnvValue -Name "DB_PORT" -Default "3306"
$dbName = Get-EnvValue -Name "DB_NAME" -Default "love_space"
$dbUser = Get-RequiredEnvValue -Name "DB_USERNAME"
$dbPassword = Get-RequiredEnvValue -Name "DB_PASSWORD"

if ($dbPassword -eq "change-me") {
    throw "DB_PASSWORD is still the example value. Edit the root .env first."
}
if ($dbName -notmatch '^[A-Za-z0-9_]+$') {
    throw "DB_NAME may only contain ASCII letters, digits, and underscores."
}

$dbPort = 0
if (-not [int]::TryParse($dbPortText, [ref]$dbPort) -or $dbPort -lt 1 -or $dbPort -gt 65535) {
    throw "DB_PORT must be an integer between 1 and 65535."
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
        "default-character-set=utf8mb4"
    ) -join [Environment]::NewLine
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
