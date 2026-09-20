# Catalog Studio — gcloud commands for GCP (Windows PowerShell).
# Requires: Google Cloud SDK, login as the billing owner.
#
#   gcloud auth login
#   .\scripts\gcloud-gcp-setup.ps1
#
# Project catalog-studio-prod may already exist. The script is safe to re-run
# for the remaining steps (billing, APIs, budget, firewall, VM, snapshots).

$ErrorActionPreference = "Stop"
$gcloud = "C:\Users\HP\AppData\Local\Google\Cloud SDK\google-cloud-sdk\bin\gcloud.cmd"
if (-not (Test-Path $gcloud)) {
  $gcloud = "gcloud"
}

$ProjectId = "catalog-studio-prod"
$ProjectName = "Catalog Studio Prod"
$BillingAccount = "012AA3-8490F8-BDB5E6"
$Region = "asia-south1"
$Zone = "asia-south1-a"
$VmName = "catalog-studio-vm"
$MachineType = "e2-medium"
$DiskGb = 50
$BudgetAmount = "50USD"
$SnapshotPolicy = "catalog-studio-daily"

function Invoke-Gcloud {
  param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
  & $gcloud @Args
  if ($LASTEXITCODE -ne 0) {
    throw "gcloud failed: $($Args -join ' ')"
  }
}

Write-Host "=== 1. Account ==="
Invoke-Gcloud auth list "--format=table(account,status)"
Invoke-Gcloud config set project $ProjectId

Write-Host "=== 2. Project (skip if it already exists) ==="
$existing = & $gcloud projects describe $ProjectId --format="value(projectId)" 2>$null
if (-not $existing) {
  Invoke-Gcloud projects create $ProjectId "--name=$ProjectName"
} else {
  Write-Host "Project $ProjectId already exists."
}

Write-Host "=== 3. Link billing ==="
Invoke-Gcloud billing projects link $ProjectId "--billing-account=$BillingAccount"
Invoke-Gcloud billing projects describe $ProjectId

Write-Host "=== 4. Enable APIs ==="
Invoke-Gcloud services enable compute.googleapis.com --project=$ProjectId
Invoke-Gcloud services enable billingbudgets.googleapis.com --project=$ProjectId

Write-Host "=== 5. Budget `$50 / month, alerts at 50% 80% 100% ==="
$budgets = & $gcloud billing budgets list --billing-account=$BillingAccount --filter="displayName:catalog-studio-prod-monthly" --format="value(name)" 2>$null
if (-not $budgets) {
  Invoke-Gcloud billing budgets create `
    --billing-account=$BillingAccount `
    --display-name="catalog-studio-prod-monthly" `
    --budget-amount=$BudgetAmount `
    --calendar-period=month `
    --filter-projects="projects/$ProjectId" `
    --threshold-rule=percent=0.5 `
    --threshold-rule=percent=0.8 `
    --threshold-rule=percent=1.0
} else {
  Write-Host "Budget catalog-studio-prod-monthly already exists."
}

Write-Host "=== 6. Firewall HTTP/HTTPS (default VPC tags) ==="
$httpRule = & $gcloud compute firewall-rules describe default-allow-http --project=$ProjectId --format="value(name)" 2>$null
if (-not $httpRule) {
  Invoke-Gcloud compute firewall-rules create default-allow-http `
    --project=$ProjectId `
    --allow=tcp:80 `
    --target-tags=http-server `
    --description="Allow HTTP to tagged VMs"
}
$httpsRule = & $gcloud compute firewall-rules describe default-allow-https --project=$ProjectId --format="value(name)" 2>$null
if (-not $httpsRule) {
  Invoke-Gcloud compute firewall-rules create default-allow-https `
    --project=$ProjectId `
    --allow=tcp:443 `
    --target-tags=https-server `
    --description="Allow HTTPS to tagged VMs"
}

Write-Host "=== 7. Daily snapshot schedule ==="
$policy = & $gcloud compute resource-policies describe $SnapshotPolicy --project=$ProjectId --region=$Region --format="value(name)" 2>$null
if (-not $policy) {
  Invoke-Gcloud compute resource-policies create snapshot-schedule $SnapshotPolicy `
    --project=$ProjectId `
    --region=$Region `
    --max-retention-days=7 `
    --on-source-disk-delete=keep-auto-snapshots `
    --daily-schedule `
    --start-time=18:00 `
    --storage-location=$Region
} else {
  Write-Host "Snapshot policy $SnapshotPolicy already exists."
}

Write-Host "=== 8. Ubuntu e2-medium VM ==="
$vm = & $gcloud compute instances describe $VmName --project=$ProjectId --zone=$Zone --format="value(name)" 2>$null
if (-not $vm) {
  Invoke-Gcloud compute instances create $VmName `
    --project=$ProjectId `
    --zone=$Zone `
    --machine-type=$MachineType `
    --image-family=ubuntu-2204-lts `
    --image-project=ubuntu-os-cloud `
    --boot-disk-size="${DiskGb}GB" `
    --boot-disk-type=pd-balanced `
    --boot-disk-device-name=$VmName `
    --tags=http-server,https-server `
    --scopes=https://www.googleapis.com/auth/cloud-platform
} else {
  Write-Host "VM $VmName already exists."
}

Write-Host "=== 9. Attach snapshot policy to boot disk ==="
Invoke-Gcloud compute disks add-resource-policies $VmName `
  --project=$ProjectId `
  --zone=$Zone `
  --resource-policies=$SnapshotPolicy

Write-Host "=== 10. External IP ==="
Invoke-Gcloud compute instances describe $VmName `
  --project=$ProjectId `
  --zone=$Zone `
  --format="value(networkInterfaces[0].accessConfigs[0].natIP)"

Write-Host @"

Next (copy the app onto the VM, then install Docker):

  gcloud compute ssh $VmName --project=$ProjectId --zone=$Zone

  gcloud compute scp --project=$ProjectId --zone=$Zone --recurse --compress ``
    --exclude="node_modules" --exclude="target" --exclude=".git" ``
    $PSScriptRoot\..\ $VmName`:/tmp/catalog-studio-upload

On the VM:

  sudo mkdir -p /opt/catalog-studio
  sudo rsync -a /tmp/catalog-studio-upload/ /opt/catalog-studio/
  sudo bash /opt/catalog-studio/scripts/gcp-vm-setup.sh

"@
