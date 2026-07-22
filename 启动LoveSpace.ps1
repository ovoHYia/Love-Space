[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSCommandPath
$startScript = Join-Path $projectRoot "scripts\start.ps1"

if (-not (Test-Path -LiteralPath $startScript -PathType Leaf)) {
    throw "找不到启动脚本：$startScript"
}

Set-Location -LiteralPath $projectRoot
Write-Host "项目目录：$projectRoot" -ForegroundColor Cyan
Write-Host "正在启动 Love Space（LAN 模式）..." -ForegroundColor Green

try {
    & $startScript -Lan
    if ($LASTEXITCODE -ne 0) {
        throw "Love Space 启动失败，退出码：$LASTEXITCODE"
    }
}
catch {
    Write-Host ""
    Write-Host "启动失败：$($_.Exception.Message)" -ForegroundColor Red
    Read-Host "按 Enter 键关闭窗口"
    exit 1
}
