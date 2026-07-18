param(
    [string]$Version = "Love-Space-v1.0-fixed"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$releaseJar = Join-Path $projectRoot "outputs\Love-Space-v1.0.jar"
$outputZip = Join-Path $projectRoot ("outputs\{0}.zip" -f $Version)
$stageRoot = Join-Path $projectRoot ("work\release-staging-{0}" -f ([guid]::NewGuid().ToString("N")))
$packageRoot = Join-Path $stageRoot "Love-Space-v1.0"

if (-not (Test-Path $releaseJar)) {
    throw "Release JAR not found: $releaseJar. Run .\\scripts\\build.ps1 first."
}

try {
    New-Item -ItemType Directory -Path $packageRoot -Force | Out-Null

    $excluded = @(".git", ".env", "data", "work", "outputs")
    Get-ChildItem -Force $projectRoot |
        Where-Object { $_.Name -notin $excluded } |
        Copy-Item -Destination $packageRoot -Recurse -Force

    $frontendNodeModules = Join-Path $packageRoot "frontend\node_modules"
    $frontendDist = Join-Path $packageRoot "frontend\dist"
    $backendTarget = Join-Path $packageRoot "backend\target"
    foreach ($path in @($frontendNodeModules, $frontendDist, $backendTarget)) {
        if (Test-Path $path) {
            Remove-Item -LiteralPath $path -Recurse -Force
        }
    }

    $releaseTarget = Join-Path $packageRoot "backend\target"
    New-Item -ItemType Directory -Path $releaseTarget -Force | Out-Null
    Copy-Item -LiteralPath $releaseJar -Destination (Join-Path $releaseTarget "love-space-backend-1.0.0.jar") -Force

    Compress-Archive -Path $packageRoot -DestinationPath $outputZip -Force
    Write-Host "Release archive created: $outputZip"
} finally {
    if (Test-Path $stageRoot) {
        Remove-Item -LiteralPath $stageRoot -Recurse -Force
    }
}
