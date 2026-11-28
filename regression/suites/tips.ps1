param([Parameter(Mandatory)] $State)

$suite = 'tips'

if (-not (Ensure-LoggedIn -State $State)) {
    return
}

$allowed = @(200, 403)

Test-ApiCheck -State $State -Suite $suite -Name 'app-guide' -Method GET -Path '/api/v1/app-guide' -Auth -AllowedStatus $allowed | Out-Null
Test-ApiCheck -State $State -Suite $suite -Name 'tutorials' -Method GET -Path '/api/v1/tutorials' -Auth -AllowedStatus $allowed | Out-Null
Test-ApiCheck -State $State -Suite $suite -Name 'tips' -Method GET -Path '/api/v1/tips' -Auth -AllowedStatus $allowed | Out-Null
