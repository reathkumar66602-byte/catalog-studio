# Shared helpers for Catalog Studio regression runner.

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function New-RegressionState {
    param(
        [Parameter(Mandatory)] [string] $EnvName,
        [Parameter(Mandatory)] [string] $Mode,
        [Parameter(Mandatory)] [string] $BaseUrl,
        [Parameter(Mandatory)] [string] $DashboardUrl,
        [string] $Email,
        [string] $Password
    )

    return [ordered]@{
        EnvName      = $EnvName
        Mode         = $Mode
        BaseUrl      = $BaseUrl.TrimEnd('/')
        DashboardUrl = $DashboardUrl.TrimEnd('/')
        Email        = $Email
        Password     = $Password
        AccessToken  = $null
        RefreshToken = $null
        StartedAt    = (Get-Date).ToUniversalTime()
        Results      = [System.Collections.Generic.List[object]]::new()
    }
}

function Add-CheckResult {
    param(
        [Parameter(Mandatory)] $State,
        [Parameter(Mandatory)] [string] $Suite,
        [Parameter(Mandatory)] [string] $Name,
        [Parameter(Mandatory)] [ValidateSet('PASS', 'FAIL', 'SKIP')] [string] $Status,
        [string] $Detail = '',
        [int] $DurationMs = 0,
        [string] $Request = ''
    )

    $State.Results.Add([ordered]@{
        suite      = $Suite
        name       = $Name
        status     = $Status
        detail     = $Detail
        durationMs = $DurationMs
        request    = $Request
        at         = (Get-Date).ToUniversalTime().ToString('o')
    })

    $color = switch ($Status) {
        'PASS' { 'Green' }
        'FAIL' { 'Red' }
        default { 'Yellow' }
    }
    Write-Host ("[{0}] {1} :: {2}" -f $Status, $Suite, $Name) -ForegroundColor $color
    if ($Detail) {
        Write-Host ("       {0}" -f $Detail) -ForegroundColor DarkGray
    }
}

function Invoke-Api {
    param(
        [Parameter(Mandatory)] $State,
        [Parameter(Mandatory)] [ValidateSet('GET', 'POST', 'PUT', 'DELETE')] [string] $Method,
        [Parameter(Mandatory)] [string] $Path,
        [object] $Body = $null,
        [switch] $Auth,
        [int] $TimeoutSec = 30,
        [int[]] $AllowedStatus = @(200, 201)
    )

    $url = if ($Path.StartsWith('http')) { $Path } else { "$($State.BaseUrl)$Path" }
    $headers = @{
        'Accept'       = 'application/json'
        'Content-Type' = 'application/json'
        'User-Agent'   = 'CatalogStudio-Regression/1.0'
    }
    if ($Auth) {
        if (-not $State.AccessToken) {
            throw 'Access token missing. Login first.'
        }
        $headers['Authorization'] = "Bearer $($State.AccessToken)"
    }

    $jsonBody = $null
    if ($null -ne $Body) {
        $jsonBody = ($Body | ConvertTo-Json -Depth 8 -Compress)
    }

    try {
        $response = Invoke-WebRequest -Uri $url -Method $Method -Headers $headers -Body $jsonBody -TimeoutSec $TimeoutSec -UseBasicParsing
        $statusCode = [int]$response.StatusCode
        $parsed = $null
        if ($response.Content) {
            try { $parsed = $response.Content | ConvertFrom-Json } catch { $parsed = $null }
        }
        return [ordered]@{
            ok         = ($AllowedStatus -contains $statusCode)
            statusCode = $statusCode
            body       = $parsed
            raw        = $response.Content
            url        = $url
            error      = $null
        }
    }
    catch {
        $statusCode = 0
        $raw = $null
        $parsed = $null
        if ($_.Exception.Response) {
            try {
                $statusCode = [int]$_.Exception.Response.StatusCode
                $stream = $_.Exception.Response.GetResponseStream()
                if ($stream) {
                    $reader = New-Object System.IO.StreamReader($stream)
                    $raw = $reader.ReadToEnd()
                    $reader.Close()
                    try { $parsed = $raw | ConvertFrom-Json } catch { $parsed = $null }
                }
            }
            catch { }
        }
        return [ordered]@{
            ok         = ($AllowedStatus -contains $statusCode)
            statusCode = $statusCode
            body       = $parsed
            raw        = $raw
            url        = $url
            error      = $_.Exception.Message
        }
    }
}

function Test-ApiCheck {
    param(
        [Parameter(Mandatory)] $State,
        [Parameter(Mandatory)] [string] $Suite,
        [Parameter(Mandatory)] [string] $Name,
        [Parameter(Mandatory)] [ValidateSet('GET', 'POST', 'PUT', 'DELETE')] [string] $Method,
        [Parameter(Mandatory)] [string] $Path,
        [object] $Body = $null,
        [switch] $Auth,
        [int[]] $AllowedStatus = @(200, 201),
        [scriptblock] $Assert = $null,
        [switch] $ExpectSuccessTrue
    )

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $res = Invoke-Api -State $State -Method $Method -Path $Path -Body $Body -Auth:$Auth -AllowedStatus $AllowedStatus
    $sw.Stop()

    $detailParts = @("HTTP $($res.statusCode)")
    if ($res.error) { $detailParts += $res.error }

    $pass = [bool]$res.ok
    if ($pass -and $ExpectSuccessTrue) {
        if (-not $res.body -or -not ($res.body.PSObject.Properties.Name -contains 'success') -or -not $res.body.success) {
            $pass = $false
            $msg = if ($res.body -and $res.body.message) { $res.body.message } else { 'success!=true' }
            $detailParts += $msg
        }
    }
    if ($pass -and $Assert) {
        try {
            & $Assert $res | Out-Null
        }
        catch {
            $pass = $false
            $detailParts += $_.Exception.Message
        }
    }

    $status = if ($pass) { 'PASS' } else { 'FAIL' }
    Add-CheckResult -State $State -Suite $Suite -Name $Name -Status $status -Detail ($detailParts -join ' | ') -DurationMs $sw.ElapsedMilliseconds -Request "$Method $Path"
    return $pass
}

function Ensure-LoggedIn {
    param([Parameter(Mandatory)] $State)

    if ($State.AccessToken) { return $true }
    if (-not $State.Email -or -not $State.Password) {
        Add-CheckResult -State $State -Suite 'auth' -Name 'login' -Status 'SKIP' -Detail 'TEST_EMAIL / TEST_PASSWORD not set'
        return $false
    }

    $body = @{
        email          = $State.Email
        password       = $State.Password
        rememberDevice = $true
        deviceName     = 'regression-runner'
    }

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $res = Invoke-Api -State $State -Method POST -Path '/api/v1/auth/login' -Body $body -AllowedStatus @(200)
    $sw.Stop()

    if (-not $res.ok -or -not $res.body -or -not $res.body.success) {
        $msg = if ($res.body -and $res.body.message) { $res.body.message } else { $res.error }
        Add-CheckResult -State $State -Suite 'auth' -Name 'login' -Status 'FAIL' -Detail "HTTP $($res.statusCode) | $msg" -DurationMs $sw.ElapsedMilliseconds -Request 'POST /api/v1/auth/login'
        return $false
    }

    $data = $res.body.data
    if ($data.otpRequired) {
        Add-CheckResult -State $State -Suite 'auth' -Name 'login' -Status 'FAIL' -Detail 'OTP required for this account. Use a test account without OTP challenge, or complete OTP outside runner.' -DurationMs $sw.ElapsedMilliseconds -Request 'POST /api/v1/auth/login'
        return $false
    }

    if (-not $data.session -or -not $data.session.accessToken) {
        Add-CheckResult -State $State -Suite 'auth' -Name 'login' -Status 'FAIL' -Detail 'Login response missing session.accessToken' -DurationMs $sw.ElapsedMilliseconds -Request 'POST /api/v1/auth/login'
        return $false
    }

    $State.AccessToken = $data.session.accessToken
    $State.RefreshToken = $data.session.refreshToken
    Add-CheckResult -State $State -Suite 'auth' -Name 'login' -Status 'PASS' -Detail "Signed in as $($State.Email)" -DurationMs $sw.ElapsedMilliseconds -Request 'POST /api/v1/auth/login'
    return $true
}

function Invoke-LocalNpmTest {
    param(
        [Parameter(Mandatory)] $State,
        [Parameter(Mandatory)] [string] $Suite,
        [Parameter(Mandatory)] [string] $Name,
        [Parameter(Mandatory)] [string] $ProjectDir,
        [Parameter(Mandatory)] [string[]] $NpmArgs,
        [int] $TimeoutSec = 180
    )

    if (-not (Test-Path $ProjectDir)) {
        Add-CheckResult -State $State -Suite $Suite -Name $Name -Status 'FAIL' -Detail "Project missing: $ProjectDir"
        return $false
    }

    $nodeCmd = Get-Command node -ErrorAction SilentlyContinue
    if (-not $nodeCmd) {
        Add-CheckResult -State $State -Suite $Suite -Name $Name -Status 'SKIP' -Detail 'node not found on PATH'
        return $false
    }

    $npmCliCandidates = @(
        (Join-Path (Split-Path $nodeCmd.Source -Parent) 'node_modules\npm\bin\npm-cli.js'),
        'C:\Program Files\nodejs\node_modules\npm\bin\npm-cli.js',
        (Join-Path $env:APPDATA 'npm\node_modules\npm\bin\npm-cli.js')
    )
    $npmCli = $npmCliCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $npmCli) {
        Add-CheckResult -State $State -Suite $Suite -Name $Name -Status 'SKIP' -Detail 'npm-cli.js not found'
        return $false
    }

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $outFile = [System.IO.Path]::GetTempFileName()
    $errFile = [System.IO.Path]::GetTempFileName()
    try {
        $argList = @($npmCli, 'test', '--') + $NpmArgs
        $proc = Start-Process -FilePath $nodeCmd.Source -ArgumentList $argList -WorkingDirectory $ProjectDir `
            -NoNewWindow -PassThru -RedirectStandardOutput $outFile -RedirectStandardError $errFile
        if (-not $proc.WaitForExit($TimeoutSec * 1000)) {
            try { $proc.Kill() } catch { }
            $sw.Stop()
            Add-CheckResult -State $State -Suite $Suite -Name $Name -Status 'FAIL' -Detail "Timed out after ${TimeoutSec}s" -DurationMs $sw.ElapsedMilliseconds -Request ("npm test -- " + ($NpmArgs -join ' '))
            return $false
        }
        $sw.Stop()
        $stdout = Get-Content $outFile -Raw -ErrorAction SilentlyContinue
        $stderr = Get-Content $errFile -Raw -ErrorAction SilentlyContinue
        $tail = (($stdout + "`n" + $stderr) -split "`n" | Where-Object { $_.Trim() } | Select-Object -Last 8) -join ' | '
        if ($proc.ExitCode -eq 0) {
            Add-CheckResult -State $State -Suite $Suite -Name $Name -Status 'PASS' -Detail $tail -DurationMs $sw.ElapsedMilliseconds -Request ("npm test -- " + ($NpmArgs -join ' '))
            return $true
        }
        Add-CheckResult -State $State -Suite $Suite -Name $Name -Status 'FAIL' -Detail ("exit $($proc.ExitCode) | " + $tail) -DurationMs $sw.ElapsedMilliseconds -Request ("npm test -- " + ($NpmArgs -join ' '))
        return $false
    }
    catch {
        $sw.Stop()
        Add-CheckResult -State $State -Suite $Suite -Name $Name -Status 'FAIL' -Detail $_.Exception.Message -DurationMs $sw.ElapsedMilliseconds -Request ("npm test -- " + ($NpmArgs -join ' '))
        return $false
    }
    finally {
        Remove-Item $outFile, $errFile -Force -ErrorAction SilentlyContinue
    }
}

function ConvertTo-HtmlEncoded {
    param([string] $Text)
    if ($null -eq $Text) { return '' }
    return [System.Net.WebUtility]::HtmlEncode($Text)
}

function Write-RegressionReport {
    param(
        [Parameter(Mandatory)] $State,
        [Parameter(Mandatory)] [string] $ReportsDir
    )

    if (-not (Test-Path $ReportsDir)) {
        New-Item -ItemType Directory -Force -Path $ReportsDir | Out-Null
    }

    $ended = (Get-Date).ToUniversalTime()
    $pass = @($State.Results | Where-Object { $_.status -eq 'PASS' }).Count
    $fail = @($State.Results | Where-Object { $_.status -eq 'FAIL' }).Count
    $skip = @($State.Results | Where-Object { $_.status -eq 'SKIP' }).Count
    $total = $State.Results.Count
    $overall = if ($fail -gt 0) { 'FAIL' } else { 'PASS' }
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $baseName = "regression-$($State.EnvName)-$stamp"
    $htmlPath = Join-Path $ReportsDir "$baseName.html"
    $jsonPath = Join-Path $ReportsDir "$baseName.json"
    $latestHtml = Join-Path $ReportsDir 'latest.html'
    $latestJson = Join-Path $ReportsDir 'latest.json'
    $zipPath = Join-Path $ReportsDir "$baseName.zip"

    $rows = foreach ($r in $State.Results) {
        $cls = $r.status.ToLowerInvariant()
        @"
<tr class="$cls">
  <td>$([System.Net.WebUtility]::HtmlEncode($r.suite))</td>
  <td>$([System.Net.WebUtility]::HtmlEncode($r.name))</td>
  <td><span class="badge $cls">$($r.status)</span></td>
  <td>$($r.durationMs) ms</td>
  <td><code>$([System.Net.WebUtility]::HtmlEncode($r.request))</code></td>
  <td>$([System.Net.WebUtility]::HtmlEncode($r.detail))</td>
</tr>
"@
    }

    $html = @"
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1" />
<title>Catalog Studio Regression — $($State.EnvName) — $overall</title>
<style>
  :root { --bg:#0f172a; --card:#1e293b; --text:#e2e8f0; --muted:#94a3b8; --pass:#22c55e; --fail:#ef4444; --skip:#eab308; --line:#334155; }
  * { box-sizing: border-box; }
  body { margin:0; font-family: Segoe UI, system-ui, sans-serif; background:linear-gradient(160deg,#0f172a,#1e293b 40%,#0b1224); color:var(--text); }
  .wrap { max-width:1100px; margin:0 auto; padding:32px 20px 64px; }
  h1 { margin:0 0 8px; font-size:1.6rem; }
  .meta { color:var(--muted); margin-bottom:20px; line-height:1.5; }
  .cards { display:grid; grid-template-columns:repeat(auto-fit,minmax(140px,1fr)); gap:12px; margin:20px 0 28px; }
  .card { background:var(--card); border:1px solid var(--line); border-radius:12px; padding:16px; }
  .card .n { font-size:1.8rem; font-weight:700; }
  .card .l { color:var(--muted); font-size:.85rem; margin-top:4px; }
  .overall.pass { color:var(--pass); }
  .overall.fail { color:var(--fail); }
  table { width:100%; border-collapse:collapse; background:var(--card); border:1px solid var(--line); border-radius:12px; overflow:hidden; }
  th, td { text-align:left; padding:10px 12px; border-bottom:1px solid var(--line); vertical-align:top; font-size:.92rem; }
  th { background:#0b1224; color:var(--muted); font-weight:600; }
  tr.fail { background:rgba(239,68,68,.08); }
  .badge { display:inline-block; padding:2px 8px; border-radius:999px; font-size:.75rem; font-weight:700; }
  .badge.pass { background:rgba(34,197,94,.15); color:var(--pass); }
  .badge.fail { background:rgba(239,68,68,.15); color:var(--fail); }
  .badge.skip { background:rgba(234,179,8,.15); color:var(--skip); }
  code { font-size:.8rem; color:#cbd5e1; }
  .actions { margin:18px 0 8px; display:flex; gap:10px; flex-wrap:wrap; }
  .actions a, .actions button { background:#334155; color:#fff; border:0; border-radius:8px; padding:8px 12px; text-decoration:none; cursor:pointer; font-size:.9rem; }
  footer { margin-top:24px; color:var(--muted); font-size:.85rem; }
</style>
</head>
<body>
  <div class="wrap">
    <h1>Catalog Studio Regression Report</h1>
    <div class="meta">
      Environment: <strong>$([System.Net.WebUtility]::HtmlEncode($State.EnvName))</strong> &nbsp;|&nbsp;
      Mode: <strong>$([System.Net.WebUtility]::HtmlEncode($State.Mode))</strong> &nbsp;|&nbsp;
      API: <code>$([System.Net.WebUtility]::HtmlEncode($State.BaseUrl))</code><br/>
      Started: $($State.StartedAt.ToString('u')) &nbsp;|&nbsp; Ended: $($ended.ToString('u'))<br/>
      Result: <strong class="overall $($overall.ToLowerInvariant())">$overall</strong>
    </div>
    <div class="cards">
      <div class="card"><div class="n">$total</div><div class="l">Total checks</div></div>
      <div class="card"><div class="n" style="color:var(--pass)">$pass</div><div class="l">Passed</div></div>
      <div class="card"><div class="n" style="color:var(--fail)">$fail</div><div class="l">Failed</div></div>
      <div class="card"><div class="n" style="color:var(--skip)">$skip</div><div class="l">Skipped</div></div>
    </div>
    <div class="actions">
      <button onclick="window.print()">Print / Save as PDF</button>
      <a href="$(Split-Path -Leaf $jsonPath)" download>Download JSON</a>
    </div>
    <table>
      <thead>
        <tr><th>Suite</th><th>Check</th><th>Status</th><th>Duration</th><th>Request</th><th>Detail</th></tr>
      </thead>
      <tbody>
        $($rows -join "`n")
      </tbody>
    </table>
    <footer>Generated by regression/run.ps1 — safe to share internally. Prod runs use read-only checks by default.</footer>
  </div>
</body>
</html>
"@

    $payload = [ordered]@{
        overall      = $overall
        env          = $State.EnvName
        mode         = $State.Mode
        baseUrl      = $State.BaseUrl
        dashboardUrl = $State.DashboardUrl
        startedAt    = $State.StartedAt.ToString('o')
        endedAt      = $ended.ToString('o')
        summary      = @{ total = $total; pass = $pass; fail = $fail; skip = $skip }
        results      = @($State.Results)
    }

    Set-Content -Path $htmlPath -Value $html -Encoding UTF8
    ($payload | ConvertTo-Json -Depth 8) | Set-Content -Path $jsonPath -Encoding UTF8
    Copy-Item $htmlPath $latestHtml -Force
    Copy-Item $jsonPath $latestJson -Force

    if (Test-Path $zipPath) { Remove-Item $zipPath -Force }
    Compress-Archive -Path $htmlPath, $jsonPath -DestinationPath $zipPath -Force

    return [ordered]@{
        overall   = $overall
        htmlPath  = $htmlPath
        jsonPath  = $jsonPath
        zipPath   = $zipPath
        latestHtml = $latestHtml
        pass      = $pass
        fail      = $fail
        skip      = $skip
        total     = $total
    }
}
