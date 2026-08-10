Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
$script:ImportedProjectEnvNames = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::OrdinalIgnoreCase)

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
        [void]$script:ImportedProjectEnvNames.Add($name)
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
            [void]$script:ImportedProjectEnvNames.Add($targetName)
        }
    }

    # DB_URL is the canonical database source for both Spring Boot and setup-db.
    # Legacy split fields are accepted only as a checked migration aid; they are
    # never used to construct a second, potentially different connection URL.
    $databaseUrl = [Environment]::GetEnvironmentVariable("DB_URL", "Process")
    $legacyDatabaseNames = @("DB_HOST", "DB_PORT", "DB_NAME")
    $legacyDatabaseValues = @{}
    foreach ($legacyName in $legacyDatabaseNames) {
        $legacyDatabaseValues[$legacyName] = [Environment]::GetEnvironmentVariable($legacyName, "Process")
    }
    $hasLegacyDatabaseValue = $legacyDatabaseValues.Values | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    if ([string]::IsNullOrWhiteSpace($databaseUrl)) {
        if ($hasLegacyDatabaseValue) {
            throw "DB_URL is required. Migrate DB_HOST/DB_PORT/DB_NAME to one canonical DB_URL value."
        }
    }
    else {
        $database = ConvertFrom-DatabaseUrl -DatabaseUrl $databaseUrl
        Assert-DatabaseUrlSecurity -Database $database
        if ($hasLegacyDatabaseValue) {
            $hasAllLegacyValues = $legacyDatabaseNames | Where-Object {
                [string]::IsNullOrWhiteSpace($legacyDatabaseValues[$_])
            } | Measure-Object | Select-Object -ExpandProperty Count
            if ($hasAllLegacyValues -ne 0 -or
                $legacyDatabaseValues["DB_HOST"] -ne $database.Host -or
                [int]$legacyDatabaseValues["DB_PORT"] -ne $database.Port -or
                $legacyDatabaseValues["DB_NAME"] -ne $database.Name) {
                throw "DB_URL conflicts with legacy DB_HOST/DB_PORT/DB_NAME values. Keep DB_URL and remove the legacy fields."
            }
        }
    }
}

function ConvertFrom-DatabaseUrl {
    param([Parameter(Mandatory = $true)][string]$DatabaseUrl)

    $pattern = '^jdbc:mysql://(?<host>\[[^\]]+\]|[^/:?#]+)(?::(?<port>[0-9]{1,5}))?/(?<name>[A-Za-z0-9_]+)(?:\?(?<query>.*))?$'
    if ($DatabaseUrl -notmatch $pattern) {
        throw "DB_URL must be a MySQL JDBC URL with a database name."
    }

    $databaseName = $Matches.name
    $databaseHost = $Matches.host
    if ($databaseHost.StartsWith("[") -and $databaseHost.EndsWith("]")) {
        $databaseHost = $databaseHost.Substring(1, $databaseHost.Length - 2)
    }
    $databasePort = if ([string]::IsNullOrWhiteSpace($Matches.port)) { 3306 } else { [int]$Matches.port }
    if ($databasePort -lt 1 -or $databasePort -gt 65535) {
        throw "DB_URL port must be an integer between 1 and 65535."
    }

    $queryValues = @{}
    if (-not [string]::IsNullOrWhiteSpace($Matches.query)) {
        foreach ($part in $Matches.query -split '&') {
            if ([string]::IsNullOrWhiteSpace($part)) { continue }
            $pair = $part -split '=', 2
            $key = [Uri]::UnescapeDataString($pair[0]).ToLowerInvariant()
            $value = if ($pair.Count -gt 1) { [Uri]::UnescapeDataString($pair[1]) } else { "" }
            $queryValues[$key] = $value
        }
    }

    $sslMode = [string]$queryValues["sslmode"]
    if ([string]::IsNullOrWhiteSpace($sslMode) -and $queryValues.ContainsKey("usessl")) {
        $sslMode = if ($queryValues["usessl"] -match '^(?i:false|0)$') { "DISABLED" } else { "REQUIRED" }
    }
    if (-not [string]::IsNullOrWhiteSpace($sslMode)) {
        $sslMode = $sslMode.ToUpperInvariant()
    }

    return [pscustomobject]@{
        Host    = $databaseHost
        Port    = $databasePort
        Name    = $databaseName
        SslMode = $sslMode
    }
}

function Test-LoopbackDatabaseHost {
    param([Parameter(Mandatory = $true)][string]$HostName)

    if ($HostName -in @("localhost", "localhost.localdomain")) { return $true }
    $address = $null
    if ([System.Net.IPAddress]::TryParse($HostName, [ref]$address)) {
        return [System.Net.IPAddress]::IsLoopback($address)
    }
    return $false
}

function Assert-DatabaseUrlSecurity {
    param([Parameter(Mandatory = $true)][pscustomobject]$Database)

    $allowedModes = @("DISABLED", "REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY")
    $tlsModes = @("REQUIRED", "VERIFY_CA", "VERIFY_IDENTITY")
    if ($Database.SslMode -notin $allowedModes) {
        throw "DB_URL must explicitly set sslMode=DISABLED for a local loopback database or a TLS mode for a remote database."
    }
    if (-not (Test-LoopbackDatabaseHost -HostName $Database.Host) -and $Database.SslMode -notin $tlsModes) {
        throw "Remote databases must require TLS with sslMode=REQUIRED, VERIFY_CA, or VERIFY_IDENTITY."
    }
}

function Get-DatabaseConnection {
    $databaseUrl = Get-RequiredEnvValue -Name "DB_URL"
    $database = ConvertFrom-DatabaseUrl -DatabaseUrl $databaseUrl
    Assert-DatabaseUrlSecurity -Database $database
    return $database
}

function Get-FrontendBlockedEnvironmentNames {
    $blocked = [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::OrdinalIgnoreCase)

    foreach ($name in $script:ImportedProjectEnvNames) {
        if ($name -notmatch '^VITE_') {
            [void]$blocked.Add($name)
        }
    }

    $backendSecretPatterns = @(
        '^(?:DB_|MYSQL_|SPRING_)',
        '^(?:SETUP_TOKEN|PASSWORD_RESET_TOKEN)$'
    )
    foreach ($entry in [Environment]::GetEnvironmentVariables("Process").GetEnumerator()) {
        $name = [string]$entry.Key
        if ($backendSecretPatterns | Where-Object { $name -match $_ }) {
            [void]$blocked.Add($name)
        }
    }
    return @($blocked)
}

function Invoke-WithFrontendEnvironment {
    param([Parameter(Mandatory = $true)][scriptblock]$ScriptBlock)

    $saved = @{}
    foreach ($name in @(Get-FrontendBlockedEnvironmentNames)) {
        $value = [Environment]::GetEnvironmentVariable($name, "Process")
        if ($null -ne $value) {
            $saved[$name] = $value
            Remove-Item -LiteralPath "Env:$name" -Force -ErrorAction SilentlyContinue
        }
    }

    try {
        & $ScriptBlock
    }
    finally {
        foreach ($name in $saved.Keys) {
            Set-Item -LiteralPath "Env:$name" -Value $saved[$name]
        }
    }
}

function Invoke-FrontendCommand {
    param(
        [Parameter(Mandatory = $true)][string]$Executable,
        [Parameter(Mandatory = $true)][string[]]$Arguments,
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][string]$Action
    )

    $process = Invoke-WithFrontendEnvironment {
        Start-Process -FilePath $Executable -ArgumentList $Arguments `
            -WorkingDirectory $WorkingDirectory -NoNewWindow -Wait -PassThru
    }
    if ($process.ExitCode -ne 0) {
        throw "$Action failed with exit code $($process.ExitCode)."
    }
}

function Assert-NodeVersion {
    param([Parameter(Mandatory = $true)][string]$Node)

    $previousErrorPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    $versionOutput = & $Node --version 2>&1
    $versionExitCode = $LASTEXITCODE
    $ErrorActionPreference = $previousErrorPreference
    if ($versionExitCode -ne 0) {
        throw "无法执行 Node.js：退出码 $versionExitCode"
    }

    $versionText = ($versionOutput | Select-Object -First 1) -join ""
    if ($versionText -notmatch '^v(?<major>\d+)\.(?<minor>\d+)\.(?<patch>\d+)') {
        throw "Cannot identify the Node.js version: $versionText"
    }
    $major = [int]$Matches.major
    $minor = [int]$Matches.minor
    $supported = ($major -eq 20 -and $minor -ge 19) -or
        ($major -ge 22 -and ($major -gt 22 -or $minor -ge 12))
    if (-not $supported) {
        throw "Node.js ^20.19.0 or >=22.12.0 is required by Vite. Current version: $versionText"
    }
    Write-Host "Node.js $versionText detected." -ForegroundColor DarkGray
}

function Assert-JarContainsStaticFrontend {
    param([Parameter(Mandatory = $true)][string]$JarPath)

    if (-not (Test-Path -LiteralPath $JarPath -PathType Leaf)) {
        throw "Cannot find packaged JAR: $JarPath"
    }
    $archive = [System.IO.Compression.ZipFile]::OpenRead($JarPath)
    try {
        $entryNames = @($archive.Entries | ForEach-Object { $_.FullName })
        foreach ($requiredEntry in @(
                "BOOT-INF/classes/static/index.html")) {
            if ($requiredEntry -notin $entryNames) {
                throw "Packaged JAR is missing $requiredEntry."
            }
        }
        if (-not ($entryNames | Where-Object { $_ -like "BOOT-INF/classes/static/assets/*" })) {
            throw "Packaged JAR contains no frontend static assets."
        }
    }
    finally {
        $archive.Dispose()
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
