[CmdletBinding()]
param(
    [switch]$SkipTests,
    [switch]$SkipInstall
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "common.ps1")

$root = Get-ProjectRoot
$backendPom = Join-Path $root "backend\pom.xml"
$frontendDir = Join-Path $root "frontend"
$frontendPackage = Join-Path $frontendDir "package.json"

Import-ProjectEnv -Path (Join-Path $root ".env") -Optional

if (-not (Test-Path -LiteralPath $backendPom -PathType Leaf)) { throw "Cannot find backend/pom.xml." }
if (-not (Test-Path -LiteralPath $frontendPackage -PathType Leaf)) { throw "Cannot find frontend/package.json." }

$java = Find-Executable -Names @("java.exe", "java")
$maven = Find-Executable -Names @("mvn.cmd", "mvn.exe", "mvn")
$npm = Find-Executable -Names @("npm.cmd", "npm.exe", "npm")
if ($null -eq $java) { throw "Cannot find Java. Install JDK 17 and configure JAVA_HOME/PATH." }
if ($null -eq $maven) { throw "Cannot find Maven. Install Maven 3.9+ and add its bin directory to PATH." }
if ($null -eq $npm) { throw "Cannot find npm. Install Node.js 20 LTS or newer." }

Push-Location $frontendDir
try {
    if (-not $SkipInstall) {
        if (Test-Path -LiteralPath (Join-Path $frontendDir "package-lock.json")) {
            Write-Host "Installing locked frontend dependencies with npm ci..." -ForegroundColor Cyan
            & $npm ci
        }
        else {
            Write-Host "Installing frontend dependencies with npm install..." -ForegroundColor Cyan
            & $npm install
        }
        Assert-LastExitCode -Action "Frontend dependency installation"
    }

    Write-Host "Building frontend..." -ForegroundColor Cyan
    & $npm run build
    Assert-LastExitCode -Action "Frontend build"
}
finally {
    Pop-Location
}

$mavenArgs = @("-f", $backendPom, "clean", "package")
if ($SkipTests) {
    $mavenArgs += "-DskipTests"
}

Write-Host "Building backend..." -ForegroundColor Cyan
& $maven @mavenArgs
Assert-LastExitCode -Action "Backend build"

Write-Host "Build completed." -ForegroundColor Green
Write-Host "Frontend output: $(Join-Path $frontendDir 'dist')"
$jar = Join-Path $root 'backend\target\love-space-backend-1.0.0.jar'
$outputs = Join-Path $root 'outputs'
[System.IO.Directory]::CreateDirectory($outputs) | Out-Null
$releaseJar = Join-Path $outputs 'Love-Space-v1.0.jar'
Copy-Item -LiteralPath $jar -Destination $releaseJar -Force
Write-Host "Single JAR:      $jar"
Write-Host "Release JAR:     $releaseJar"
