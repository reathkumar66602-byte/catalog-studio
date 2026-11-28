param([Parameter(Mandatory)] $State)

$suite = 'templates'

if (-not (Ensure-LoggedIn -State $State)) {
    return
}

$allowed = @(200, 403)

Test-ApiCheck -State $State -Suite $suite -Name 'listing-templates' -Method GET -Path '/api/v1/templates' -Auth -AllowedStatus $allowed | Out-Null
Test-ApiCheck -State $State -Suite $suite -Name 'autofill-profiles' -Method GET -Path '/api/v1/profiles' -Auth -AllowedStatus $allowed | Out-Null

if ($State.Mode -eq 'safe') {
    Add-CheckResult -State $State -Suite $suite -Name 'template-write' -Status 'SKIP' -Detail 'Template/profile create disabled in safe/prod mode'
}
else {
    Add-CheckResult -State $State -Suite $suite -Name 'template-write' -Status 'SKIP' -Detail 'Writes not auto-run yet'
}
