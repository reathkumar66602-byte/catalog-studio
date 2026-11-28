param([Parameter(Mandatory)] $State)

$suite = 'smoke'

Test-ApiCheck -State $State -Suite $suite -Name 'dashboard-reachable' -Method GET -Path $State.DashboardUrl -AllowedStatus @(200) | Out-Null

Test-ApiCheck -State $State -Suite $suite -Name 'public-site-api' -Method GET -Path '/api/v1/site' -AllowedStatus @(200) -ExpectSuccessTrue | Out-Null

Test-ApiCheck -State $State -Suite $suite -Name 'subscription-plans-public-or-auth' -Method GET -Path '/api/v1/subscriptions/plans' -AllowedStatus @(200, 401) | Out-Null

# Public marketing / guest tool entry pages (SPA shell must load)
foreach ($page in @(
        @{ Name = 'page-login'; Path = '/login' },
        @{ Name = 'page-privacy'; Path = '/privacy' },
        @{ Name = 'page-contact'; Path = '/contact' },
        @{ Name = 'page-chrome-extension'; Path = '/chrome-extension' }
    )) {
    Test-ApiCheck -State $State -Suite $suite -Name $page.Name -Method GET -Path "$($State.DashboardUrl)$($page.Path)" -AllowedStatus @(200) | Out-Null
}

# Gateway routing probes (unauthenticated): must not be gateway 502/404 for known prefixes.
# 401/403 = route reached a service (good). 200 = public ok.
foreach ($probe in @(
        @{ Name = 'gateway-platform-me-route'; Path = '/api/v1/me'; Allowed = @(200, 401, 403) },
        @{ Name = 'gateway-wholesale-route'; Path = '/api/v1/wholesale/categories'; Allowed = @(200, 401, 403) },
        @{ Name = 'gateway-supplier-route'; Path = '/api/v1/wholesale/supplier/store'; Allowed = @(200, 401, 403) },
        @{ Name = 'gateway-support-route'; Path = '/api/v1/support/categories'; Allowed = @(200, 401, 403) },
        @{ Name = 'gateway-extension-route'; Path = '/api/v1/extension/ping'; Allowed = @(200, 400, 401, 403) },
        @{ Name = 'gateway-oms-route'; Path = '/api/v1/oms/orders'; Allowed = @(200, 401, 403) },
        @{ Name = 'gateway-inventory-route'; Path = '/api/v1/inventory'; Allowed = @(200, 401, 403) }
    )) {
    Test-ApiCheck -State $State -Suite $suite -Name $probe.Name -Method GET -Path $probe.Path -AllowedStatus $probe.Allowed | Out-Null
}
