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
$frontendDist = Join-Path $frontendDir "dist"

if (-not (Test-Path -LiteralPath $backendPom -PathType Leaf)) { throw "Cannot find backend/pom.xml." }
if (-not (Test-Path -LiteralPath $frontendPackage -PathType Leaf)) { throw "Cannot find frontend/package.json." }

$java = Find-Executable -Names @("java.exe", "java")
$maven = Find-Executable -Names @("mvn.cmd", "mvn.exe", "mvn")
$npm = Find-Executable -Names @("npm.cmd", "npm.exe", "npm")
$node = Find-Executable -Names @("node.exe", "node")
if ($null -eq $java) { throw "Cannot find Java. Install JDK 17 and configure JAVA_HOME/PATH." }
if ($null -eq $maven) { throw "Cannot find Maven. Install Maven 3.9+ and add its bin directory to PATH." }
if ($null -eq $npm) { throw "Cannot find npm. Install Node.js ^20.19.0 or >=22.12.0." }
if ($null -eq $node) { throw "Cannot find Node.js. Install Node.js ^20.19.0 or >=22.12.0." }
Assert-NodeVersion -Node $node

if (Test-Path -LiteralPath $frontendDist) {
    Write-Host "Removing previous frontend/dist before the production build..." -ForegroundColor DarkGray
    Remove-Item -LiteralPath $frontendDist -Recurse -Force
}

Push-Location $frontendDir
try {
    if (-not $SkipInstall) {
        if (Test-Path -LiteralPath (Join-Path $frontendDir "package-lock.json")) {
            Write-Host "Installing locked frontend dependencies with npm ci..." -ForegroundColor Cyan
            Invoke-FrontendCommand -Executable $npm -Arguments @("ci") `
                -WorkingDirectory $frontendDir -Action "Frontend dependency installation"
        }
        else {
            Write-Host "Installing frontend dependencies with npm install..." -ForegroundColor Cyan
            Invoke-FrontendCommand -Executable $npm -Arguments @("install") `
                -WorkingDirectory $frontendDir -Action "Frontend dependency installation"
        }
    }

    if (-not $SkipTests) {
        Write-Host "Running frontend tests..." -ForegroundColor Cyan
        Invoke-FrontendCommand -Executable $npm -Arguments @("test") `
            -WorkingDirectory $frontendDir -Action "Frontend tests"
    }
    else {
        Write-Host "Skipping frontend and backend tests because -SkipTests was supplied." -ForegroundColor Yellow
    }

    Write-Host "Building frontend..." -ForegroundColor Cyan
    Invoke-FrontendCommand -Executable $npm -Arguments @("run", "build") `
        -WorkingDirectory $frontendDir -Action "Frontend build"
}
finally {
    Pop-Location
}

# Only load backend settings after every frontend child process has finished.
# Invoke-FrontendCommand also strips backend variables from the npm environment.
Import-ProjectEnv -Path (Join-Path $root ".env") -Optional

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
Assert-JarContainsStaticFrontend -JarPath $jar
$outputs = Join-Path $root 'outputs'
[System.IO.Directory]::CreateDirectory($outputs) | Out-Null
$releaseJar = Join-Path $outputs 'Love-Space-v1.0.jar'
Copy-Item -LiteralPath $jar -Destination $releaseJar -Force
Write-Host "Single JAR:      $jar"
Write-Host "Release JAR:     $releaseJar"
