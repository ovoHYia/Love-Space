[CmdletBinding()]
param(
    [string]$EnvFile,
    [switch]$UpdateEnv
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "common.ps1")

if ([string]::IsNullOrWhiteSpace($EnvFile)) {
    $EnvFile = Join-Path (Get-ProjectRoot) ".env"
}
$resolvedEnvFile = [System.IO.Path]::GetFullPath($EnvFile)
$token = [Convert]::ToHexString(
    [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
).ToLowerInvariant()

if (-not $UpdateEnv) {
    Write-Output $token
    Write-Host "请将该 256 位随机值写入 PASSWORD_RESET_TOKEN，并重启后端。" -ForegroundColor Yellow
    return
}

if (-not (Test-Path -LiteralPath $resolvedEnvFile -PathType Leaf)) {
    throw "找不到环境文件：$resolvedEnvFile"
}

$lines = [System.IO.File]::ReadAllLines($resolvedEnvFile)
$updatedLines = [System.Collections.Generic.List[string]]::new()
$found = $false
foreach ($line in $lines) {
    if ($line -match '^\s*(?:export\s+)?PASSWORD_RESET_TOKEN\s*=') {
        if (-not $found) {
            $updatedLines.Add("PASSWORD_RESET_TOKEN=$token")
            $found = $true
        }
        continue
    }
    $updatedLines.Add($line)
}
if (-not $found) {
    $updatedLines.Add("PASSWORD_RESET_TOKEN=$token")
}

$temporaryFile = "$resolvedEnvFile.$([Guid]::NewGuid().ToString('N')).tmp"
try {
    [System.IO.File]::WriteAllLines(
        $temporaryFile,
        $updatedLines,
        [System.Text.UTF8Encoding]::new($false)
    )
    Move-Item -LiteralPath $temporaryFile -Destination $resolvedEnvFile -Force
}
finally {
    if (Test-Path -LiteralPath $temporaryFile) {
        Remove-Item -LiteralPath $temporaryFile -Force
    }
}

$digest = [Security.Cryptography.SHA256]::HashData(
    [System.Text.Encoding]::UTF8.GetBytes($token)
)
$fingerprint = [Convert]::ToHexString($digest[0..5]).ToLowerInvariant()
Write-Host "已轮换 PASSWORD_RESET_TOKEN（SHA-256 指纹：$fingerprint）。请重启后端使新 token 生效。" -ForegroundColor Green
