[CmdletBinding()]
param(
    [switch]$SkipInstall
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "common.ps1")

$root = Get-ProjectRoot
$backendDir = Join-Path $root "backend"
$frontendDir = Join-Path $root "frontend"
$logsDir = Join-Path $root "work\logs"
$frontendPackage = Join-Path $frontendDir "package.json"
$frontendLock = Join-Path $frontendDir "package-lock.json"
$frontendModules = Join-Path $frontendDir "node_modules"
$frontendLockStamp = Join-Path $frontendModules ".love-space-package-lock.sha256"

if (-not (Test-Path -LiteralPath (Join-Path $backendDir "pom.xml") -PathType Leaf)) {
    throw "Cannot find backend/pom.xml."
}
if (-not (Test-Path -LiteralPath $frontendPackage -PathType Leaf)) {
    throw "Cannot find frontend/package.json."
}

$java = Find-Executable -Names @("java.exe", "java")
$maven = Find-Executable -Names @("mvn.cmd", "mvn.exe", "mvn")
$npm = Find-Executable -Names @("npm.cmd", "npm.exe", "npm")
$node = Find-Executable -Names @("node.exe", "node")
if ($null -eq $java) { throw "Cannot find Java. Install JDK 17 and configure JAVA_HOME/PATH." }
if ($null -eq $maven) { throw "Cannot find Maven. Install Maven 3.9+ and add its bin directory to PATH." }
if ($null -eq $npm) { throw "Cannot find npm. Install Node.js ^20.19.0 or >=22.12.0." }
if ($null -eq $node) { throw "Cannot find Node.js. Install Node.js ^20.19.0 or >=22.12.0." }
Assert-NodeVersion -Node $node

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

$needsFrontendInstall = -not (Test-Path -LiteralPath $frontendModules -PathType Container)
$currentLockHash = $null
if (Test-Path -LiteralPath $frontendLock -PathType Leaf) {
    $currentLockHash = (Get-FileHash -LiteralPath $frontendLock -Algorithm SHA256).Hash
    $installedLockHash = if (Test-Path -LiteralPath $frontendLockStamp -PathType Leaf) {
        (Get-Content -Raw -LiteralPath $frontendLockStamp).Trim()
    }
    else {
        ""
    }
    if ($installedLockHash -ne $currentLockHash) {
        $needsFrontendInstall = $true
    }
}

if ($SkipInstall -and $needsFrontendInstall) {
    throw "frontend dependencies are missing or package-lock.json changed; remove -SkipInstall so start-dev can run npm ci."
}

if ($needsFrontendInstall) {
    Push-Location $frontendDir
    try {
        if (Test-Path -LiteralPath $frontendLock -PathType Leaf) {
            Write-Host "First run: installing frontend dependencies with npm ci..." -ForegroundColor Cyan
            Invoke-FrontendCommand -Executable $npm -Arguments @("ci") `
                -WorkingDirectory $frontendDir -Action "Frontend dependency installation"
            [System.IO.File]::WriteAllText($frontendLockStamp, $currentLockHash, [System.Text.UTF8Encoding]::new($false))
        }
        else {
            Write-Host "First run: installing frontend dependencies with npm install..." -ForegroundColor Cyan
            Invoke-FrontendCommand -Executable $npm -Arguments @("install") `
                -WorkingDirectory $frontendDir -Action "Frontend dependency installation"
        }
    }
    finally {
        Pop-Location
    }
}

# Load backend settings only after npm has finished. The frontend dev process
# is started through Invoke-WithFrontendEnvironment below, so it cannot inherit
# backend credentials either.
Import-ProjectEnv -Path (Join-Path $root ".env")

$configuredUploadDir = Get-EnvValue -Name "UPLOAD_DIR" -Default ".\data\uploads"
if ([System.IO.Path]::IsPathRooted($configuredUploadDir)) {
    $uploadDir = [System.IO.Path]::GetFullPath($configuredUploadDir)
}
else {
    $uploadDir = [System.IO.Path]::GetFullPath((Join-Path $root $configuredUploadDir))
}
[Environment]::SetEnvironmentVariable("UPLOAD_DIR", $uploadDir, "Process")

[System.IO.Directory]::CreateDirectory($logsDir) | Out-Null
[System.IO.Directory]::CreateDirectory($uploadDir) | Out-Null

$backendOut = Join-Path $logsDir "backend.out.log"
$backendErr = Join-Path $logsDir "backend.err.log"
$frontendOut = Join-Path $logsDir "frontend.out.log"
$frontendErr = Join-Path $logsDir "frontend.err.log"
foreach ($log in @($backendOut, $backendErr, $frontendOut, $frontendErr)) {
    if (-not (Test-Path -LiteralPath $log)) { continue }
    try {
        # Rotate instead of deleting so an orphan process holding the handle
        # cannot turn log cleanup into a cryptic Remove-Item failure.
        Move-Item -LiteralPath $log -Destination "$log.prev" -Force
    }
    catch {
        Write-Host "警告：无法轮转日志 $log（可能仍被旧进程占用），本次运行将继续追加。" -ForegroundColor Yellow
    }
}

$frontendHost = Get-EnvValue -Name "FRONTEND_HOST" -Default "127.0.0.1"
$frontendPort = Get-EnvValue -Name "FRONTEND_PORT" -Default "5173"
$backendPort = Get-EnvValue -Name "SERVER_PORT" -Default "8080"
foreach ($portValue in @($frontendPort, $backendPort)) {
    if ($portValue -notmatch '^\d{1,5}$') {
        throw "FRONTEND_PORT/SERVER_PORT 必须是数字端口，当前值：$portValue"
    }
}

function Test-PortInUse {
    param([int]$Port)
    $listeners = @(Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
    return $listeners.Count -gt 0
}

# Fail early when a previous run (or another app) still holds the ports;
# otherwise the new processes die immediately with confusing errors.
foreach ($portCheck in @(@($frontendPort, "前端"), @($backendPort, "后端"))) {
    if (Test-PortInUse -Port ([int]$portCheck[0])) {
        throw "端口 $($portCheck[0]) 已被占用（$($portCheck[1])）。请先停止占用该端口的进程（如旧的 start-dev 会话），再重新运行。"
    }
}

$backend = $null
$frontend = $null

function Stop-ProcessTree {
    param(
        [System.Diagnostics.Process]$Process,
        [int]$TimeoutSeconds = 10
    )
    if ($null -eq $Process) { return }
    try {
        $Process.Refresh()
        if ($Process.HasExited) { return }
        # Try a graceful stop first so the JVM can run its shutdown hooks;
        # only force kill after the timeout expires.
        & taskkill.exe /PID $Process.Id /T 2>$null | Out-Null
        if (-not $Process.WaitForExit($TimeoutSeconds * 1000)) {
            & taskkill.exe /PID $Process.Id /T /F 2>$null | Out-Null
        }
    }
    catch {
        # The process may already have exited between Refresh and taskkill.
    }
}

try {
    Write-Host "Starting backend and frontend in hidden child processes..." -ForegroundColor Cyan
    $backend = Start-Process -FilePath $maven `
        -ArgumentList @("-f", "backend\pom.xml", "spring-boot:run") `
        -WorkingDirectory $root `
        -RedirectStandardOutput $backendOut `
        -RedirectStandardError $backendErr `
        -WindowStyle Hidden `
        -PassThru

    $frontend = Invoke-WithFrontendEnvironment {
        Start-Process -FilePath $npm `
            -ArgumentList @("run", "dev", "--", "--host", $frontendHost, "--port", $frontendPort, "--strictPort") `
            -WorkingDirectory $frontendDir `
            -RedirectStandardOutput $frontendOut `
            -RedirectStandardError $frontendErr `
            -WindowStyle Hidden `
            -PassThru
    }

    Write-Host "Love Space development environment is running." -ForegroundColor Green
    Write-Host "Frontend: http://localhost:$frontendPort"
    Write-Host "Backend:  http://localhost:$backendPort/api/health"
    Write-Host "Media:    $uploadDir"
    Write-Host "Logs:     $logsDir"
    Write-Host "Press Ctrl+C to stop both services."

    while ($true) {
        Start-Sleep -Seconds 1
        $backend.Refresh()
        $frontend.Refresh()

        if ($backend.HasExited) {
            Write-Host "Backend exited with code $($backend.ExitCode). See $backendErr" -ForegroundColor Red
            if (Test-Path -LiteralPath $backendErr) { Get-Content -LiteralPath $backendErr -Tail 20 }
            throw "Backend development process stopped unexpectedly."
        }
        if ($frontend.HasExited) {
            Write-Host "Frontend exited with code $($frontend.ExitCode). See $frontendErr" -ForegroundColor Red
            if (Test-Path -LiteralPath $frontendErr) { Get-Content -LiteralPath $frontendErr -Tail 20 }
            throw "Frontend development process stopped unexpectedly."
        }
    }
}
finally {
    Write-Host "Stopping the Love Space development environment..." -ForegroundColor Yellow
    Stop-ProcessTree -Process $frontend
    Stop-ProcessTree -Process $backend
}
