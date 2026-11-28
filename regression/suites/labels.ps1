param([Parameter(Mandatory)] $State)

$suite = 'labels'

# PDF crop/merge is client-side. Prove:
# 1) public guest tool routes still resolve on the deployed dashboard
# 2) local Vitest suites for crop/merge math still pass (code regression)

$pages = @(
    @{ Name = 'page-flipkart-crop'; Path = '/shipping-label-crop' },
    @{ Name = 'page-meesho-crop'; Path = '/meesho-shipping-label-crop' },
    @{ Name = 'page-merge-pdf'; Path = '/merge-pdf' }
)

foreach ($p in $pages) {
    Test-ApiCheck -State $State -Suite $suite -Name $p.Name -Method GET -Path "$($State.DashboardUrl)$($p.Path)" -AllowedStatus @(200) | Out-Null
}

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$dashRoot = Join-Path $repoRoot 'catalog-studio-dashboard'
if (Test-Path (Join-Path $dashRoot 'package.json')) {
    Invoke-LocalNpmTest -State $State -Suite $suite -Name 'pdf-layout-math' -ProjectDir $dashRoot `
        -NpmArgs @('src/features/labels/pdf/labelsPdf.test.ts', 'src/features/labels/pdf/detectLayout.test.ts') | Out-Null

    # realDownloads can be slower / file-heavy — still include for full confidence
    Invoke-LocalNpmTest -State $State -Suite $suite -Name 'pdf-real-downloads' -ProjectDir $dashRoot `
        -NpmArgs @('src/features/labels/pdf/realDownloads.test.ts') -TimeoutSec 240 | Out-Null
}
else {
    Add-CheckResult -State $State -Suite $suite -Name 'pdf-layout-math' -Status 'SKIP' -Detail 'dashboard project not found'
}
