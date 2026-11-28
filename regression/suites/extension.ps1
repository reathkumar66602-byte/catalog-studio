param([Parameter(Mandatory)] $State)

$suite = 'extension'

if (-not (Ensure-LoggedIn -State $State)) {
    return
}

# Extension device endpoints need X-Extension-Key; 400/401 without it still proves the route is live.
$allowed = @(200, 400, 401, 403)

Test-ApiCheck -State $State -Suite $suite -Name 'ping' -Method GET -Path '/api/v1/extension/ping' -Auth -AllowedStatus $allowed | Out-Null
Test-ApiCheck -State $State -Suite $suite -Name 'devices' -Method GET -Path '/api/v1/extension/devices' -Auth -AllowedStatus $allowed | Out-Null
Test-ApiCheck -State $State -Suite $suite -Name 'workspace' -Method GET -Path '/api/v1/extension/workspace' -Auth -AllowedStatus $allowed | Out-Null
Test-ApiCheck -State $State -Suite $suite -Name 'settings' -Method GET -Path '/api/v1/extension/settings' -Auth -AllowedStatus $allowed | Out-Null
Test-ApiCheck -State $State -Suite $suite -Name 'products' -Method GET -Path '/api/v1/extension/products' -Auth -AllowedStatus $allowed | Out-Null
Test-ApiCheck -State $State -Suite $suite -Name 'profiles' -Method GET -Path '/api/v1/extension/profiles' -Auth -AllowedStatus $allowed | Out-Null
Test-ApiCheck -State $State -Suite $suite -Name 'categories' -Method GET -Path '/api/v1/extension/categories' -Auth -AllowedStatus $allowed | Out-Null

# Public marketing page for extension
Test-ApiCheck -State $State -Suite $suite -Name 'chrome-extension-page' -Method GET -Path "$($State.DashboardUrl)/chrome-extension" -AllowedStatus @(200) | Out-Null

# Local autofill unit tests (client-side; always useful after code changes).
# Run autofill + content-bundle explicitly so failures are attributable.
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$extRoot = Join-Path $repoRoot 'catalog-studio-extension'
if (Test-Path (Join-Path $extRoot 'package.json')) {
    Invoke-LocalNpmTest -State $State -Suite $suite -Name 'autofill-unit-tests' -ProjectDir $extRoot `
        -NpmArgs @('tests/autofill.test.ts', 'tests/content-bundle.test.ts', '--pool=forks', '--maxWorkers=1') `
        -TimeoutSec 240 | Out-Null
}
else {
    Add-CheckResult -State $State -Suite $suite -Name 'autofill-unit-tests' -Status 'SKIP' -Detail 'catalog-studio-extension not found'
}

if ($State.Mode -eq 'safe') {
    Add-CheckResult -State $State -Suite $suite -Name 'pair-unpair' -Status 'SKIP' -Detail 'Pair/unpair writes disabled in safe/prod mode'
}
else {
    Add-CheckResult -State $State -Suite $suite -Name 'pair-unpair' -Status 'SKIP' -Detail 'Pairing needs a real Chrome device — cover via API reads + unit tests'
}
