# Build the production Chrome extension zip (points at https://www.catalogstudio.in).
# Usage from repo root:  powershell -File scripts/build-extension-prod.ps1
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location (Join-Path $root "catalog-studio-extension")

if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
  throw "npm is required. Install Node.js 20+ first."
}

npm install
if ($LASTEXITCODE -ne 0) { throw "npm install failed" }

npm run build:prod
if ($LASTEXITCODE -ne 0) { throw "npm run build:prod failed" }

$zip = Get-ChildItem -Path (Join-Path (Get-Location) "release\catalog-studio-extension-*.zip") | Sort-Object LastWriteTime -Descending | Select-Object -First 1
Write-Host ""
Write-Host "Production zip: $($zip.FullName)"
Write-Host "Test unpacked from: $(Join-Path (Get-Location) 'dist-prod')"
Write-Host "Publish steps: docs/CHROME_EXTENSION_PUBLISH.md"
