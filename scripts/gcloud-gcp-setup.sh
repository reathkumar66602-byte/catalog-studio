#!/usr/bin/env bash
# Catalog Studio — gcloud commands (Cloud Shell or Git Bash).
# Edit the variables, then:  bash scripts/gcloud-gcp-setup.sh
set -euo pipefail

PROJECT_ID="${PROJECT_ID:-catalog-studio-prod}"
PROJECT_NAME="${PROJECT_NAME:-Catalog Studio Prod}"
BILLING_ACCOUNT="${BILLING_ACCOUNT:-012AA3-8490F8-BDB5E6}"
REGION="${REGION:-asia-south1}"
ZONE="${ZONE:-asia-south1-a}"
VM_NAME="${VM_NAME:-catalog-studio-vm}"
MACHINE_TYPE="${MACHINE_TYPE:-e2-medium}"
DISK_GB="${DISK_GB:-50}"
BUDGET_AMOUNT="${BUDGET_AMOUNT:-50USD}"
SNAPSHOT_POLICY="${SNAPSHOT_POLICY:-catalog-studio-daily}"

echo "=== 1. Account ==="
gcloud auth list --format="table(account,status)"
gcloud config set project "$PROJECT_ID"

echo "=== 2. Project ==="
if ! gcloud projects describe "$PROJECT_ID" >/dev/null 2>&1; then
  gcloud projects create "$PROJECT_ID" --name="$PROJECT_NAME"
fi

echo "=== 3. Link billing ==="
gcloud billing projects link "$PROJECT_ID" --billing-account="$BILLING_ACCOUNT"
gcloud billing projects describe "$PROJECT_ID"

echo "=== 4. Enable APIs ==="
gcloud services enable compute.googleapis.com --project="$PROJECT_ID"
gcloud services enable billingbudgets.googleapis.com --project="$PROJECT_ID"

echo "=== 5. Budget \$50 / month, alerts at 50% 80% 100% ==="
if ! gcloud billing budgets list --billing-account="$BILLING_ACCOUNT" \
    --filter="displayName:catalog-studio-prod-monthly" \
    --format="value(name)" | grep -q .; then
  gcloud billing budgets create \
    --billing-account="$BILLING_ACCOUNT" \
    --display-name="catalog-studio-prod-monthly" \
    --budget-amount="$BUDGET_AMOUNT" \
    --calendar-period=month \
    --filter-projects="projects/${PROJECT_ID}" \
    --threshold-rule=percent=0.5 \
    --threshold-rule=percent=0.8 \
    --threshold-rule=percent=1.0
fi

echo "=== 6. Firewall HTTP/HTTPS ==="
gcloud compute firewall-rules describe default-allow-http --project="$PROJECT_ID" >/dev/null 2>&1 || \
  gcloud compute firewall-rules create default-allow-http \
    --project="$PROJECT_ID" --allow=tcp:80 --target-tags=http-server
gcloud compute firewall-rules describe default-allow-https --project="$PROJECT_ID" >/dev/null 2>&1 || \
  gcloud compute firewall-rules create default-allow-https \
    --project="$PROJECT_ID" --allow=tcp:443 --target-tags=https-server

echo "=== 7. Daily snapshot schedule ==="
gcloud compute resource-policies describe "$SNAPSHOT_POLICY" --project="$PROJECT_ID" --region="$REGION" >/dev/null 2>&1 || \
  gcloud compute resource-policies create snapshot-schedule "$SNAPSHOT_POLICY" \
    --project="$PROJECT_ID" \
    --region="$REGION" \
    --max-retention-days=7 \
    --on-source-disk-delete=keep-auto-snapshots \
    --daily-schedule \
    --start-time=18:00 \
    --storage-location="$REGION"

echo "=== 8. Ubuntu e2-medium VM ==="
gcloud compute instances describe "$VM_NAME" --project="$PROJECT_ID" --zone="$ZONE" >/dev/null 2>&1 || \
  gcloud compute instances create "$VM_NAME" \
    --project="$PROJECT_ID" \
    --zone="$ZONE" \
    --machine-type="$MACHINE_TYPE" \
    --image-family=ubuntu-2204-lts \
    --image-project=ubuntu-os-cloud \
    --boot-disk-size="${DISK_GB}GB" \
    --boot-disk-type=pd-balanced \
    --boot-disk-device-name="$VM_NAME" \
    --tags=http-server,https-server \
    --scopes=https://www.googleapis.com/auth/cloud-platform

echo "=== 9. Attach snapshot policy ==="
gcloud compute disks add-resource-policies "$VM_NAME" \
  --project="$PROJECT_ID" \
  --zone="$ZONE" \
  --resource-policies="$SNAPSHOT_POLICY" || true

echo "=== 10. External IP ==="
gcloud compute instances describe "$VM_NAME" \
  --project="$PROJECT_ID" \
  --zone="$ZONE" \
  --format="value(networkInterfaces[0].accessConfigs[0].natIP)"

cat <<EOF

SSH:
  gcloud compute ssh $VM_NAME --project=$PROJECT_ID --zone=$ZONE

On the VM, after the repo is in /opt/catalog-studio:
  sudo bash /opt/catalog-studio/scripts/gcp-vm-setup.sh
EOF
