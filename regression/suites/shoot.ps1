param([Parameter(Mandatory)] $State)

$suite = 'shoot'

if (-not (Ensure-LoggedIn -State $State)) {
    return
}

Test-ApiCheck -State $State -Suite $suite -Name 'list-shoots' -Method GET -Path '/api/v1/shoots' -Auth -AllowedStatus @(200, 403) | Out-Null

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$dashRoot = Join-Path $repoRoot 'catalog-studio-dashboard'
if (Test-Path (Join-Path $dashRoot 'package.json')) {
    Invoke-LocalNpmTest -State $State -Suite $suite -Name 'shoot-contract-unit' -ProjectDir $dashRoot `
        -NpmArgs @('src/features/shoot/ShootBackendContract.test.ts', 'src/features/shoot/ShootPage.test.ts') | Out-Null
}

if ($State.Mode -eq 'safe') {
    Add-CheckResult -State $State -Suite $suite -Name 'create-shoot' -Status 'SKIP' -Detail 'Shoot create (multipart AI) disabled in safe/prod mode'
}
else {
    Add-CheckResult -State $State -Suite $suite -Name 'create-shoot' -Status 'SKIP' -Detail 'Multipart shoot create not auto-run — costs AI credits'
}
