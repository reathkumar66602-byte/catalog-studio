param([Parameter(Mandatory)] $State)

$suite = 'billing'

if (-not (Ensure-LoggedIn -State $State)) {
    return
}

Test-ApiCheck -State $State -Suite $suite -Name 'plans' -Method GET -Path '/api/v1/subscriptions/plans' -Auth -ExpectSuccessTrue -Assert {
    param($res)
    if ($null -eq $res.body.data) { throw 'plans data missing' }
} | Out-Null

Test-ApiCheck -State $State -Suite $suite -Name 'current-subscription' -Method GET -Path '/api/v1/subscriptions/current' -Auth -ExpectSuccessTrue | Out-Null

Test-ApiCheck -State $State -Suite $suite -Name 'me-entitlements' -Method GET -Path '/api/v1/me' -Auth -ExpectSuccessTrue -Assert {
    param($res)
    if ($null -eq $res.body.data.enabledFeatures) { throw 'me.enabledFeatures missing' }
} | Out-Null

if ($State.Mode -eq 'safe') {
    Add-CheckResult -State $State -Suite $suite -Name 'checkout' -Status 'SKIP' -Detail 'Checkout disabled in safe/prod mode'
}
else {
    Add-CheckResult -State $State -Suite $suite -Name 'checkout' -Status 'SKIP' -Detail 'Checkout not auto-run — verify manually with Cashfree sandbox if needed'
}
