param([Parameter(Mandatory)] $State)

$suite = 'catalog'

if (-not (Ensure-LoggedIn -State $State)) {
    return
}

Test-ApiCheck -State $State -Suite $suite -Name 'list-products' -Method GET -Path '/api/v1/products?page=0&size=5' -Auth -AllowedStatus @(200, 403) | Out-Null

Test-ApiCheck -State $State -Suite $suite -Name 'analysis-history' -Method GET -Path '/api/v1/analysis?page=0&size=5' -Auth -AllowedStatus @(200, 403) | Out-Null

if ($State.Mode -eq 'safe') {
    Add-CheckResult -State $State -Suite $suite -Name 'product-analyze-write' -Status 'SKIP' -Detail 'Analyze upload disabled in safe/prod mode'
}
else {
    Add-CheckResult -State $State -Suite $suite -Name 'product-analyze-write' -Status 'SKIP' -Detail 'Multipart analyze not auto-run — costs AI credits'
}
