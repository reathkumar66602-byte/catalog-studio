param([Parameter(Mandatory)] $State)

$suite = 'calculator'

# Meesho profit calculator is client-side. Prove public route + math tests.
Test-ApiCheck -State $State -Suite $suite -Name 'public-calculator-page' -Method GET -Path "$($State.DashboardUrl)/calculator" -AllowedStatus @(200) | Out-Null

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$dashRoot = Join-Path $repoRoot 'catalog-studio-dashboard'
if (Test-Path (Join-Path $dashRoot 'package.json')) {
    Invoke-LocalNpmTest -State $State -Suite $suite -Name 'meesho-math-unit' -ProjectDir $dashRoot `
        -NpmArgs @('src/features/calculator/meeshoMath.test.ts') | Out-Null
    Invoke-LocalNpmTest -State $State -Suite $suite -Name 'site-calculator-unit' -ProjectDir $dashRoot `
        -NpmArgs @('src/features/site/calculator.test.ts') | Out-Null
}
else {
    Add-CheckResult -State $State -Suite $suite -Name 'meesho-math-unit' -Status 'SKIP' -Detail 'dashboard project not found'
}
