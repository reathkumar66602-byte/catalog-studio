param([Parameter(Mandatory)] $State)

$suite = 'support'

if (-not (Ensure-LoggedIn -State $State)) {
    return
}

Test-ApiCheck -State $State -Suite $suite -Name 'categories' -Method GET -Path '/api/v1/support/categories' -Auth -AllowedStatus @(200, 403) | Out-Null

Test-ApiCheck -State $State -Suite $suite -Name 'my-tickets' -Method GET -Path '/api/v1/support/tickets?page=0&size=5' -Auth -AllowedStatus @(200, 403) | Out-Null

if ($State.Mode -eq 'safe') {
    Add-CheckResult -State $State -Suite $suite -Name 'create-ticket' -Status 'SKIP' -Detail 'Ticket create disabled in safe/prod mode'
}
else {
    $cats = Invoke-Api -State $State -Method GET -Path '/api/v1/support/categories' -Auth -AllowedStatus @(200, 403)
    $categoryCode = $null
    if ($cats.ok -and $cats.body -and $cats.body.success -and $cats.body.data -and @($cats.body.data).Count -gt 0) {
        $categoryCode = @($cats.body.data)[0].code
    }

    if (-not $categoryCode) {
        Add-CheckResult -State $State -Suite $suite -Name 'create-ticket' -Status 'SKIP' -Detail 'No support category available to create ticket'
        return
    }

    $body = @{
        subject       = "REGRESSION $(Get-Date -Format 'yyyy-MM-dd HH:mm') — auto"
        description   = 'Auto-created by regression/run.ps1. Safe to close.'
        categoryCode  = $categoryCode
        priority      = 'LOW'
    }

    Test-ApiCheck -State $State -Suite $suite -Name 'create-ticket' -Method POST -Path '/api/v1/support/tickets' -Body $body -Auth -AllowedStatus @(200, 201) -ExpectSuccessTrue | Out-Null
}
