param([Parameter(Mandatory)] $State)

$suite = 'auth'

if (-not (Ensure-LoggedIn -State $State)) {
    return
}

Test-ApiCheck -State $State -Suite $suite -Name 'me' -Method GET -Path '/api/v1/me' -Auth -ExpectSuccessTrue -Assert {
    param($res)
    if (-not $res.body.data.email) { throw 'me.data.email missing' }
} | Out-Null

Test-ApiCheck -State $State -Suite $suite -Name 'dashboard-stats' -Method GET -Path '/api/v1/dashboard' -Auth -AllowedStatus @(200, 403) | Out-Null

Test-ApiCheck -State $State -Suite $suite -Name 'sessions' -Method GET -Path '/api/v1/me/sessions' -Auth -ExpectSuccessTrue | Out-Null

if ($State.RefreshToken) {
    $refreshBody = @{ refreshToken = $State.RefreshToken }
    $res = Invoke-Api -State $State -Method POST -Path '/api/v1/auth/refresh-token' -Body $refreshBody -AllowedStatus @(200)
    if ($res.ok -and $res.body -and $res.body.success -and $res.body.data.accessToken) {
        $State.AccessToken = $res.body.data.accessToken
        if ($res.body.data.refreshToken) { $State.RefreshToken = $res.body.data.refreshToken }
        Add-CheckResult -State $State -Suite $suite -Name 'refresh-token' -Status 'PASS' -Detail 'New access token issued' -Request 'POST /api/v1/auth/refresh-token'
    }
    else {
        $msg = if ($res.body -and $res.body.message) { $res.body.message } else { $res.error }
        Add-CheckResult -State $State -Suite $suite -Name 'refresh-token' -Status 'FAIL' -Detail "HTTP $($res.statusCode) | $msg" -Request 'POST /api/v1/auth/refresh-token'
    }
}

# Logout only in full mode — prod safe keeps the session for later checks in same run.
if ($State.Mode -eq 'full' -and $State.RefreshToken) {
    $logoutBody = @{ refreshToken = $State.RefreshToken }
    Test-ApiCheck -State $State -Suite $suite -Name 'logout' -Method POST -Path '/api/v1/auth/logout' -Body $logoutBody -ExpectSuccessTrue | Out-Null
    # Re-login so later suites still work in the same run.
    $State.AccessToken = $null
    $State.RefreshToken = $null
    Ensure-LoggedIn -State $State | Out-Null
}
else {
    Add-CheckResult -State $State -Suite $suite -Name 'logout' -Status 'SKIP' -Detail 'Skipped in safe mode (prod). Use -Mode full on staging/local to exercise logout.'
}
