param([Parameter(Mandatory)] $State)

$suite = 'wholesale'

if (-not (Ensure-LoggedIn -State $State)) {
    return
}

# These prove gateway → supplier-service still routes and JWT is accepted.
Test-ApiCheck -State $State -Suite $suite -Name 'categories' -Method GET -Path '/api/v1/wholesale/categories' -Auth -AllowedStatus @(200, 403) | Out-Null

Test-ApiCheck -State $State -Suite $suite -Name 'stores' -Method GET -Path '/api/v1/wholesale/stores?page=0&size=5' -Auth -AllowedStatus @(200, 403) | Out-Null

Test-ApiCheck -State $State -Suite $suite -Name 'products' -Method GET -Path '/api/v1/wholesale/products?page=0&size=5' -Auth -AllowedStatus @(200, 403) | Out-Null

Test-ApiCheck -State $State -Suite $suite -Name 'buyer-orders-list' -Method GET -Path '/api/v1/wholesale/buyer/orders?page=0&size=5' -Auth -AllowedStatus @(200, 403, 404) | Out-Null

if ($State.Mode -eq 'safe') {
    Add-CheckResult -State $State -Suite $suite -Name 'place-order' -Status 'SKIP' -Detail 'Order placement disabled in safe/prod mode'
}
else {
    Add-CheckResult -State $State -Suite $suite -Name 'place-order' -Status 'SKIP' -Detail 'Full write journey not enabled yet — add when staging seed catalog exists'
}
