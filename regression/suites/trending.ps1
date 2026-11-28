param([Parameter(Mandatory)] $State)

$suite = 'trending'

if (-not (Ensure-LoggedIn -State $State)) {
    return
}

$allowed = @(200, 403)

Test-ApiCheck -State $State -Suite $suite -Name 'categories' -Method GET -Path '/api/v1/trending/categories' -Auth -AllowedStatus $allowed | Out-Null

# Products require marketplace query param.
foreach ($mp in @('meesho', 'flipkart', 'amazon')) {
    Test-ApiCheck -State $State -Suite $suite -Name "products-$mp" -Method GET `
        -Path "/api/v1/trending/products?marketplace=$mp&category=all&page=1" -Auth -AllowedStatus $allowed | Out-Null
}
