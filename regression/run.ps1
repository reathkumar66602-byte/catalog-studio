<#
.SYNOPSIS
  Manual Catalog Studio regression runner (solo / main-branch workflow).

.DESCRIPTION
  Hit the live or staging API through the public gateway, run smoke + journey checks,
  write an HTML/JSON/ZIP report under regression/reports/, and optionally open it.

.PARAMETER Env
  Target environment: local | staging | prod

.PARAMETER Mode
  safe  = read-only (default for prod)
  full  = allows limited writes (staging/local only)

.PARAMETER Suite
  Comma-separated suites. Default: all
  smoke,auth,catalog,templates,wholesale,supplier,inventory,oms,extension,
  labels,calculator,trending,shoot,tips,support,billing

.PARAMETER OpenReport
  Open the HTML report in the default browser when finished.

.EXAMPLE
  .\run.ps1 -Env prod -OpenReport

.EXAMPLE
  .\run.ps1 -Env staging -Mode full -OpenReport

.EXAMPLE
  .\run.ps1 -Env local -Suite smoke,auth
#>
[CmdletBinding()]
param(
    [ValidateSet('local', 'staging', 'prod')]
    [string] $Env = 'local',

    [ValidateSet('safe', 'full')]
    [string] $Mode = '',

    [string] $Suite = 'all',

    [switch] $OpenReport,

    [string] $BaseUrl = '',
    [string] $DashboardUrl = '',
    [string] $Email = '',
    [string] $Password = ''
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$Root = $PSScriptRoot
. (Join-Path $Root 'lib\Common.ps1')

# Load optional .env next to this script (KEY=VALUE lines).
$envFile = Join-Path $Root '.env'
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        $line = $_.Trim()
        if (-not $line -or $line.StartsWith('#')) { return }
        $idx = $line.IndexOf('=')
        if ($idx -lt 1) { return }
        $key = $line.Substring(0, $idx).Trim()
        $val = $line.Substring($idx + 1).Trim().Trim('"').Trim("'")
        if (-not [string]::IsNullOrWhiteSpace($key)) {
            Set-Item -Path "Env:$key" -Value $val
        }
    }
}

$defaults = @{
    local = @{
        BaseUrl      = 'http://localhost:8080'
        DashboardUrl = 'http://localhost:5173'
    }
    staging = @{
        BaseUrl      = $(if ($env:STAGING_BASE_URL) { $env:STAGING_BASE_URL } else { 'https://staging.catalogstudio.in' })
        DashboardUrl = $(if ($env:STAGING_DASHBOARD_URL) { $env:STAGING_DASHBOARD_URL } else { 'https://staging.catalogstudio.in' })
    }
    prod = @{
        BaseUrl      = $(if ($env:PROD_BASE_URL) { $env:PROD_BASE_URL } else { 'https://www.catalogstudio.in' })
        DashboardUrl = $(if ($env:PROD_DASHBOARD_URL) { $env:PROD_DASHBOARD_URL } else { 'https://www.catalogstudio.in' })
    }
}

if (-not $Mode) {
    $Mode = if ($Env -eq 'prod') { 'safe' } else { 'safe' }
}

if ($Env -eq 'prod' -and $Mode -eq 'full') {
    Write-Host ''
    Write-Host 'REFUSING: -Mode full is not allowed on prod.' -ForegroundColor Red
    Write-Host 'Prod always runs in safe (read-only) mode so live data is not mutated.' -ForegroundColor Yellow
    Write-Host 'Use:  .\run.ps1 -Env prod -OpenReport' -ForegroundColor Yellow
    exit 2
}

if (-not $BaseUrl) { $BaseUrl = $defaults[$Env].BaseUrl }
if (-not $DashboardUrl) { $DashboardUrl = $defaults[$Env].DashboardUrl }
if (-not $Email) { $Email = $env:TEST_EMAIL }
if (-not $Password) { $Password = $env:TEST_PASSWORD }

# Allow env-specific credentials
if ($Env -eq 'prod') {
    if ($env:PROD_TEST_EMAIL) { $Email = $env:PROD_TEST_EMAIL }
    if ($env:PROD_TEST_PASSWORD) { $Password = $env:PROD_TEST_PASSWORD }
}
elseif ($Env -eq 'staging') {
    if ($env:STAGING_TEST_EMAIL) { $Email = $env:STAGING_TEST_EMAIL }
    if ($env:STAGING_TEST_PASSWORD) { $Password = $env:STAGING_TEST_PASSWORD }
}

$State = New-RegressionState -EnvName $Env -Mode $Mode -BaseUrl $BaseUrl -DashboardUrl $DashboardUrl -Email $Email -Password $Password

Write-Host ''
Write-Host 'Catalog Studio regression' -ForegroundColor Cyan
Write-Host ("  Env:        {0}" -f $Env)
Write-Host ("  Mode:       {0}" -f $Mode)
Write-Host ("  API:        {0}" -f $State.BaseUrl)
Write-Host ("  Dashboard:  {0}" -f $State.DashboardUrl)
Write-Host ("  Account:    {0}" -f $(if ($Email) { $Email } else { '(not set — auth suites will SKIP)' }))
Write-Host ''

$allSuites = @(
    'smoke',
    'auth',
    'catalog',
    'templates',
    'wholesale',
    'supplier',
    'inventory',
    'oms',
    'extension',
    'labels',
    'calculator',
    'trending',
    'shoot',
    'tips',
    'support',
    'billing'
)
$selected = if ($Suite -eq 'all') {
    $allSuites
}
else {
    $Suite.Split(',') | ForEach-Object { $_.Trim().ToLowerInvariant() } | Where-Object { $_ }
}

foreach ($name in $selected) {
    $path = Join-Path $Root "suites\$name.ps1"
    if (-not (Test-Path $path)) {
        Add-CheckResult -State $State -Suite $name -Name 'suite-file' -Status 'FAIL' -Detail "Missing $path"
        continue
    }
    Write-Host ("--- suite: {0} ---" -f $name) -ForegroundColor Cyan
    & $path -State $State
}

$report = Write-RegressionReport -State $State -ReportsDir (Join-Path $Root 'reports')

Write-Host ''
Write-Host 'Report ready' -ForegroundColor Cyan
Write-Host ("  HTML:  {0}" -f $report.htmlPath)
Write-Host ("  JSON:  {0}" -f $report.jsonPath)
Write-Host ("  ZIP:   {0}" -f $report.zipPath)
Write-Host ("  Latest:{0}" -f $report.latestHtml)
Write-Host ("  Score: {0}/{1} passed, {2} failed, {3} skipped — {4}" -f $report.pass, $report.total, $report.fail, $report.skip, $report.overall)

if ($OpenReport) {
    Start-Process $report.htmlPath
    # Also open the folder so ZIP is easy to grab/share
    Start-Process explorer.exe -ArgumentList "/select,`"$($report.zipPath)`""
}

if ($report.fail -gt 0) { exit 1 } else { exit 0 }
