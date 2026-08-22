[CmdletBinding()]
param(
    [switch]$Lan
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "common.ps1")

$root = Get-ProjectRoot
$jar = Join-Path $root "backend\target\love-space-backend-1.0.0.jar"
Import-ProjectEnv -Path (Join-Path $root ".env")
$activeProfiles = Get-EnvValue -Name "SPRING_PROFILES_ACTIVE"
$isProduction = @($activeProfiles -split "\s*,\s*") -contains "prod"

if ($isProduction -and $Lan) {
    throw "生产环境禁止使用 -Lan；请保持 Spring Boot 仅监听回环地址，并通过 HTTPS 反向代理提供服务。"
}

if (-not $isProduction) {
    Write-Host "警告：未启用 prod profile，生产安全校验不会生效；仅用于本地或可信局域网。" -ForegroundColor Yellow
}

if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) {
    throw "Cannot find $jar. Run: powershell -ExecutionPolicy Bypass -File .\scripts\build.ps1"
}

$java = Find-Executable -Names @("java.exe", "java")
if ($null -eq $java) {
    throw "Cannot find Java. Install JDK 17 and configure JAVA_HOME/PATH."
}

$previousErrorPreference = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$javaVersionOutput = & $java -version 2>&1
$javaVersionExitCode = $LASTEXITCODE
$ErrorActionPreference = $previousErrorPreference
if ($javaVersionExitCode -ne 0) {
    throw "无法执行 Java：退出码 $javaVersionExitCode"
}
$javaVersionText = ($javaVersionOutput | Select-Object -First 1) -join ""
if ($javaVersionText -notmatch 'version "(?<major>\d+)') {
    throw "Cannot identify the Java version: $javaVersionText"
}
if ([int]$Matches.major -lt 17) {
    throw "JDK 17 or newer is required. Current version: $javaVersionText"
}

if ($Lan) {
    [Environment]::SetEnvironmentVariable("SERVER_ADDRESS", "0.0.0.0", "Process")
}
elseif ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable("SERVER_ADDRESS", "Process"))) {
    [Environment]::SetEnvironmentVariable("SERVER_ADDRESS", "127.0.0.1", "Process")
}

[System.IO.Directory]::CreateDirectory((Join-Path $root "data\uploads")) | Out-Null

$port = Get-EnvValue -Name "SERVER_PORT" -Default "8080"
if ($port -notmatch '^\d{1,5}$') {
    throw "SERVER_PORT 必须是数字端口，当前值：$port"
}
$listenAddress = Get-EnvValue -Name "SERVER_ADDRESS" -Default "127.0.0.1"
if ($isProduction -and $listenAddress -notin @("127.0.0.1", "::1", "localhost")) {
    throw "生产环境 SERVER_ADDRESS 必须是回环地址，当前值：$listenAddress"
}
Write-Host "Starting the Love Space single JAR..." -ForegroundColor Cyan
if ($listenAddress -eq "0.0.0.0") {
    Write-Host "LAN mode is enabled. Open one of these URLs on your phone:" -ForegroundColor Yellow
    $lanAddresses = @(Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object {
            $_.IPAddress -ne "127.0.0.1" -and
            $_.IPAddress -notlike "169.254.*" -and
            $_.InterfaceAlias -notmatch 'Loopback|vEthernet|WSL|VMware|VirtualBox'
        })
    if ($lanAddresses.Count -gt 0) {
        foreach ($lanAddress in $lanAddresses) {
            Write-Host ("  http://{0}:{1}  ({2})" -f $lanAddress.IPAddress, $port, $lanAddress.InterfaceAlias)
        }
    }
    else {
        Write-Host "  http://YOUR-PC-LAN-IP:$port (no LAN IPv4 address was detected)"
    }
    Write-Host "Use this only on a trusted private network and allow TCP $port only."
}
else {
    Write-Host "Local URL: http://localhost:$port"
    Write-Host "For phone access, stop and rerun with .\scripts\start.ps1 -Lan."
}
Write-Host "Press Ctrl+C to stop."

Push-Location $root
try {
    & $java -jar $jar
    Assert-LastExitCode -Action "Love Space"
}
finally {
    Pop-Location
}
