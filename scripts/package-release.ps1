[CmdletBinding()]
param(
    [string]$Version = "Love-Space-v1.0-fixed"
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
. (Join-Path $PSScriptRoot "common.ps1")
if ($Version -notmatch '^[A-Za-z0-9._-]+$') {
    throw "-Version 只能包含字母、数字、点、下划线和连字符，当前值：$Version"
}
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

    foreach ($file in @(".env.example", ".gitignore", "AGENTS.md", "README.md")) {
        Copy-Item -LiteralPath (Join-Path $projectRoot $file) -Destination $packageRoot -Force
    }

    $backendRoot = Join-Path $packageRoot "backend"
    New-Item -ItemType Directory -Path $backendRoot -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $projectRoot "backend\pom.xml") -Destination $backendRoot -Force
    Copy-Item -LiteralPath (Join-Path $projectRoot "backend\src") -Destination $backendRoot -Recurse -Force

    $frontendRoot = Join-Path $packageRoot "frontend"
    New-Item -ItemType Directory -Path $frontendRoot -Force | Out-Null
    foreach ($file in @(".env.example", ".gitignore", "index.html", "package.json", "package-lock.json",
            "tsconfig.app.json", "tsconfig.json", "tsconfig.node.json", "vite.config.ts")) {
        Copy-Item -LiteralPath (Join-Path $projectRoot "frontend\$file") -Destination $frontendRoot -Force
    }
    foreach ($directory in @("public", "src")) {
        Copy-Item -LiteralPath (Join-Path $projectRoot "frontend\$directory") -Destination $frontendRoot -Recurse -Force
    }

    Copy-Item -LiteralPath (Join-Path $projectRoot "scripts") -Destination $packageRoot -Recurse -Force

    $releaseTarget = Join-Path $packageRoot "backend\target"
    New-Item -ItemType Directory -Path $releaseTarget -Force | Out-Null
    Copy-Item -LiteralPath $releaseJar -Destination (Join-Path $releaseTarget "love-space-backend-1.0.0.jar") -Force
    Assert-JarContainsStaticFrontend -JarPath (Join-Path $packageRoot "backend\target\love-space-backend-1.0.0.jar")

    $forbidden = Get-ChildItem -LiteralPath $packageRoot -Recurse -Force -File |
        Where-Object {
            $relativePath = $_.FullName.Substring($packageRoot.Length + 1)
            $_.Name -match '^\.env(?!\.example$)' -or
            $_.Extension -in @('.pem', '.key', '.p12', '.pfx', '.jks', '.db', '.sqlite', '.sqlite3', '.log') -or
            $_.FullName -match '[\\/](data|uploads|backup|backups|node_modules)[\\/]' -or
            ($_.FullName -match '[\\/]target[\\/]' -and
                $relativePath -ne 'backend\target\love-space-backend-1.0.0.jar')
        }
    if ($forbidden) {
        $relative = $forbidden | ForEach-Object { $_.FullName.Substring($packageRoot.Length + 1) }
        throw "Release staging contains forbidden files: $($relative -join ', ')"
    }

    Compress-Archive -Path $packageRoot -DestinationPath $outputZip -Force
    Write-Host "Release archive created: $outputZip"
} finally {
    if (Test-Path $stageRoot) {
        Remove-Item -LiteralPath $stageRoot -Recurse -Force
    }
}
