# Creates catalog_studio_support if missing (local Postgres).
# Uses DATABASE_USER / DATABASE_PASSWORD from the environment, else postgres / root.

$ErrorActionPreference = "Stop"

$user = if ($env:DATABASE_USER) { $env:DATABASE_USER } elseif ($env:SUPPORT_DATABASE_USER) { $env:SUPPORT_DATABASE_USER } else { "postgres" }
$password = if ($env:DATABASE_PASSWORD) { $env:DATABASE_PASSWORD } elseif ($env:SUPPORT_DATABASE_PASSWORD) { $env:SUPPORT_DATABASE_PASSWORD } else { "root" }
$hostName = if ($env:PGHOST) { $env:PGHOST } else { "localhost" }
$port = if ($env:PGPORT) { $env:PGPORT } else { "5432" }
$dbName = "catalog_studio_support"

$env:PGPASSWORD = $password

$psql = Get-Command psql -ErrorAction SilentlyContinue
if (-not $psql) {
    $candidates = @(
        "C:\Program Files\PostgreSQL\17\bin\psql.exe",
        "C:\Program Files\PostgreSQL\16\bin\psql.exe",
        "C:\Program Files\PostgreSQL\15\bin\psql.exe"
    )
    foreach ($c in $candidates) {
        if (Test-Path $c) {
            $psql = Get-Item $c
            break
        }
    }
}
if (-not $psql) {
    Write-Error "psql not found. Install PostgreSQL client tools or add psql to PATH."
}

$psqlExe = if ($psql -is [System.Management.Automation.CommandInfo]) { $psql.Source } else { $psql.FullName }

Write-Host "==> Ensuring database '$dbName' exists (user=$user@${hostName}:${port})"

$exists = & $psqlExe -h $hostName -p $port -U $user -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='$dbName'"
if ($exists -match "1") {
    Write-Host "Database '$dbName' already exists."
    exit 0
}

& $psqlExe -h $hostName -p $port -U $user -d postgres -c "CREATE DATABASE $dbName OWNER $user;"
Write-Host "Created database '$dbName'."
