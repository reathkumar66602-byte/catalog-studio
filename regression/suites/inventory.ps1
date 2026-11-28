param([Parameter(Mandatory)] $State)

$suite = 'inventory'

if (-not (Ensure-LoggedIn -State $State)) {
    return
}

$allowed = @(200, 403)

Test-ApiCheck -State $State -Suite $suite -Name 'inventory-list' -Method GET -Path '/api/v1/inventory' -Auth -AllowedStatus $allowed | Out-Null

# Variant list needs a product id — try first product if list works.
$products = Invoke-Api -State $State -Method GET -Path '/api/v1/products?page=0&size=1' -Auth -AllowedStatus @(200, 403)
if ($products.ok -and $products.body -and $products.body.success) {
    $items = $null
    $data = $products.body.data
    if ($data.content) { $items = @($data.content) }
    elseif ($data.items) { $items = @($data.items) }
    elseif ($data -is [System.Array]) { $items = @($data) }

    if ($items -and $items.Count -gt 0) {
        $pid = $items[0].id
        if (-not $pid) { $pid = $items[0].uuid }
        if ($pid) {
            Test-ApiCheck -State $State -Suite $suite -Name 'product-variants' -Method GET -Path "/api/v1/products/$pid/variants" -Auth -AllowedStatus $allowed | Out-Null
        }
        else {
            Add-CheckResult -State $State -Suite $suite -Name 'product-variants' -Status 'SKIP' -Detail 'Product id missing on first product'
        }
    }
    else {
        Add-CheckResult -State $State -Suite $suite -Name 'product-variants' -Status 'SKIP' -Detail 'No products available for variant check'
    }
}
else {
    Add-CheckResult -State $State -Suite $suite -Name 'product-variants' -Status 'SKIP' -Detail 'Could not list products (auth/entitlement)'
}

if ($State.Mode -eq 'safe') {
    Add-CheckResult -State $State -Suite $suite -Name 'inventory-adjust' -Status 'SKIP' -Detail 'Stock adjust disabled in safe/prod mode'
}
else {
    Add-CheckResult -State $State -Suite $suite -Name 'inventory-adjust' -Status 'SKIP' -Detail 'Adjust not auto-run — avoid mutating live stock unless seeded staging SKU exists'
}
