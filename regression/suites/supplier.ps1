param([Parameter(Mandatory)] $State)

$suite = 'supplier'

if (-not (Ensure-LoggedIn -State $State)) {
    return
}

# Wholesale supplier domain (supplier-service via gateway).
# 200 = entitled supplier; 403 = feature/plan gated (route still healthy); 404 rare.
$allowed = @(200, 403)

Test-ApiCheck -State $State -Suite $suite -Name 'store' -Method GET -Path '/api/v1/wholesale/supplier/store' -Auth -AllowedStatus $allowed | Out-Null
Test-ApiCheck -State $State -Suite $suite -Name 'products' -Method GET -Path '/api/v1/wholesale/supplier/products?page=0&size=5' -Auth -AllowedStatus $allowed | Out-Null
Test-ApiCheck -State $State -Suite $suite -Name 'orders' -Method GET -Path '/api/v1/wholesale/supplier/orders?page=0&size=5' -Auth -AllowedStatus $allowed | Out-Null
Test-ApiCheck -State $State -Suite $suite -Name 'coupons' -Method GET -Path '/api/v1/wholesale/supplier/coupons' -Auth -AllowedStatus $allowed | Out-Null
Test-ApiCheck -State $State -Suite $suite -Name 'bills' -Method GET -Path '/api/v1/wholesale/supplier/bills?page=0&size=5' -Auth -AllowedStatus $allowed | Out-Null

if ($State.Mode -eq 'safe') {
    Add-CheckResult -State $State -Suite $suite -Name 'gst-lookup-write' -Status 'SKIP' -Detail 'Supplier writes disabled in safe/prod mode'
}
else {
    Add-CheckResult -State $State -Suite $suite -Name 'gst-lookup-write' -Status 'SKIP' -Detail 'GST lookup / product publish not auto-run — use staging seed when ready'
}
