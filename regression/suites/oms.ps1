param([Parameter(Mandatory)] $State)

$suite = 'oms'

if (-not (Ensure-LoggedIn -State $State)) {
    return
}

$allowed = @(200, 403)

Test-ApiCheck -State $State -Suite $suite -Name 'marketplaces' -Method GET -Path '/api/v1/marketplaces' -Auth -AllowedStatus $allowed | Out-Null

Test-ApiCheck -State $State -Suite $suite -Name 'oms-orders' -Method GET -Path '/api/v1/oms/orders?channel=ALL' -Auth -AllowedStatus $allowed | Out-Null
Test-ApiCheck -State $State -Suite $suite -Name 'oms-listings' -Method GET -Path '/api/v1/oms/listings' -Auth -AllowedStatus $allowed | Out-Null
Test-ApiCheck -State $State -Suite $suite -Name 'oms-auto-actions' -Method GET -Path '/api/v1/oms/auto-actions' -Auth -AllowedStatus $allowed | Out-Null
Test-ApiCheck -State $State -Suite $suite -Name 'oms-couriers' -Method GET -Path '/api/v1/oms/couriers' -Auth -AllowedStatus $allowed | Out-Null

Test-ApiCheck -State $State -Suite $suite -Name 'meesho-panel' -Method GET -Path '/api/v1/marketplaces/meesho/panel' -Auth -AllowedStatus $allowed | Out-Null
Test-ApiCheck -State $State -Suite $suite -Name 'meesho-orders' -Method GET -Path '/api/v1/marketplaces/meesho/panel/orders' -Auth -AllowedStatus $allowed | Out-Null
Test-ApiCheck -State $State -Suite $suite -Name 'meesho-sync-runs' -Method GET -Path '/api/v1/marketplaces/meesho/panel/sync-runs' -Auth -AllowedStatus $allowed | Out-Null

Test-ApiCheck -State $State -Suite $suite -Name 'flipkart-seller-api' -Method GET -Path '/api/v1/marketplaces/flipkart/seller-api' -Auth -AllowedStatus $allowed | Out-Null
Test-ApiCheck -State $State -Suite $suite -Name 'amazon-sp-api' -Method GET -Path '/api/v1/marketplaces/amazon/sp-api' -Auth -AllowedStatus $allowed | Out-Null

if ($State.Mode -eq 'safe') {
    Add-CheckResult -State $State -Suite $suite -Name 'oms-sync-write' -Status 'SKIP' -Detail 'OMS sync/ship writes disabled in safe/prod mode'
}
else {
    Add-CheckResult -State $State -Suite $suite -Name 'oms-sync-write' -Status 'SKIP' -Detail 'Sync endpoints not auto-run — can spam marketplace APIs'
}
