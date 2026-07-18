Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-ProjectRoot {
    return [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot ".."))
}

function Import-ProjectEnv {
    param(
        [string]$Path = (Join-Path (Get-ProjectRoot) ".env"),
        [switch]$Optional
    )

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        if ($Optional) {
            return
        }

        throw "Missing $Path. Run 'Copy-Item .env.example .env' in the project root, then fill in the real settings."
    }

    foreach ($rawLine in [System.IO.File]::ReadAllLines($Path)) {
        $line = $rawLine.Trim()
        if ($line.Length -eq 0 -or $line.StartsWith("#")) {
            continue
        }

        if ($line.StartsWith("export ")) {
            $line = $line.Substring(7).TrimStart()
        }

        $separator = $line.IndexOf("=")
        if ($separator -lt 1) {
            throw "Cannot parse this .env line: $rawLine"
        }

        $name = $line.Substring(0, $separator).Trim()
        $value = $line.Substring($separator + 1).Trim()
        if ($name -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
            throw "Invalid variable name in .env: $name"
        }

        if ($value.Length -ge 2) {
            $first = $value[0]
            $last = $value[$value.Length - 1]
            if (($first -eq '"' -and $last -eq '"') -or ($first -eq "'" -and $last -eq "'")) {
                $value = $value.Substring(1, $value.Length - 2)
            }
        }

        [Environment]::SetEnvironmentVariable($name, $value, "Process")
    }

    # Accept names used by early local snapshots while keeping the public
    # template aligned with Spring Boot's canonical environment variables.
    $aliases = @{
        "BACKEND_PORT"            = "SERVER_PORT"
        "APP_CORS_ALLOWED_ORIGINS" = "CORS_ALLOWED_ORIGINS"
        "LOVE_STORAGE_DIR"         = "UPLOAD_DIR"
    }
    foreach ($sourceName in $aliases.Keys) {
        $targetName = $aliases[$sourceName]
        $sourceValue = [Environment]::GetEnvironmentVariable($sourceName, "Process")
        $targetValue = [Environment]::GetEnvironmentVariable($targetName, "Process")
        if (-not [string]::IsNullOrWhiteSpace($sourceValue) -and [string]::IsNullOrWhiteSpace($targetValue)) {
            [Environment]::SetEnvironmentVariable($targetName, $sourceValue, "Process")
        }
    }

    # setup-db.ps1 uses the split fields; the backend consumes DB_URL. Derive
    # the latter when an older .env only contains DB_HOST/PORT/NAME.
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable("DB_URL", "Process"))) {
        $dbHost = [Environment]::GetEnvironmentVariable("DB_HOST", "Process")
        $dbPort = [Environment]::GetEnvironmentVariable("DB_PORT", "Process")
        $dbName = [Environment]::GetEnvironmentVariable("DB_NAME", "Process")
        if (-not [string]::IsNullOrWhiteSpace($dbHost) -and
            -not [string]::IsNullOrWhiteSpace($dbPort) -and
            -not [string]::IsNullOrWhiteSpace($dbName)) {
            $dbUrl = "jdbc:mysql://${dbHost}:${dbPort}/${dbName}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false"
            [Environment]::SetEnvironmentVariable("DB_URL", $dbUrl, "Process")
        }
    }
}

function Get-EnvValue {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [string]$Default = ""
    )

    $value = [Environment]::GetEnvironmentVariable($Name, "Process")
    if ([string]::IsNullOrWhiteSpace($value)) {
        return $Default
    }

    return $value
}

function Get-RequiredEnvValue {
    param([Parameter(Mandatory = $true)][string]$Name)

    $value = Get-EnvValue -Name $Name
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Missing required .env value: $Name"
    }

    return $value
}

function Find-Executable {
    param(
        [Parameter(Mandatory = $true)][string[]]$Names,
        [string[]]$FallbackPatterns = @()
    )

    foreach ($name in $Names) {
        $command = Get-Command $name -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($null -ne $command) {
            return $command.Source
        }
    }

    foreach ($pattern in $FallbackPatterns) {
        $match = Get-Item -Path $pattern -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending |
            Select-Object -First 1
        if ($null -ne $match) {
            return $match.FullName
        }
    }

    return $null
}

function Assert-LastExitCode {
    param([Parameter(Mandatory = $true)][string]$Action)

    if ($LASTEXITCODE -ne 0) {
        throw "$Action failed with exit code $LASTEXITCODE."
    }
}
