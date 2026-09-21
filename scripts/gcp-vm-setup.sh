#!/usr/bin/env bash
# Run on the Ubuntu VM after the Catalog Studio repo is in /opt/catalog-studio.
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/catalog-studio}"
SWAP_GB="${SWAP_GB:-4}"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root: sudo bash scripts/gcp-vm-setup.sh"
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y ca-certificates curl gnupg

if ! command -v docker >/dev/null 2>&1; then
  install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
  echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
    > /etc/apt/sources.list.d/docker.list
  apt-get update -y
  apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
fi

systemctl enable --now docker

if [[ ! -f /swapfile ]]; then
  fallocate -l "${SWAP_GB}G" /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

EXTERNAL_IP="$(curl -fsS -H 'Metadata-Flavor: Google' http://metadata.google.internal/computeMetadata/v1/instance/network-interfaces/0/access-configs/0/external-ip)"
PUBLIC_ORIGIN="http://${EXTERNAL_IP}"

cd "$APP_DIR"

if [[ ! -f .env.gcp ]]; then
  JWT_SECRET="$(openssl rand -base64 48 | tr -d '\n')"
  DATABASE_PASSWORD="$(openssl rand -base64 18 | tr -d '/+' | head -c 24)"
  cat > .env.gcp <<EOF
PUBLIC_ORIGIN=https://www.catalogstudio.in
CORS_ALLOWED_ORIGINS=https://catalogstudio.in,https://www.catalogstudio.in,http://catalogstudio.in,http://www.catalogstudio.in,http://${EXTERNAL_IP}
JWT_SECRET=${JWT_SECRET}
DATABASE_PASSWORD=${DATABASE_PASSWORD}
AI_PROVIDER=openai
AI_API_KEY=
AI_MODEL=gpt-4o-mini
MAIL_ENABLED=true
MAIL_PROVIDER=zoho
ZOHO_MAIL_HOST=smtppro.zoho.in
ZOHO_MAIL_PORT=465
ZOHO_MAIL_USERNAME=support@catalogstudio.in
ZOHO_MAIL_PASSWORD=
ZOHO_MAIL_FROM=support@catalogstudio.in
ZOHO_MAIL_FROM_NAME=Catalog Studio
MAIL_SUPPORT_INBOX=support@catalogstudio.in
ZEPTOMAIL_SEND_TOKEN=
GOOGLE_MAPS_API_KEY=
SUPPORT_WHATSAPP_NUMBER=917290942427
EOF
  chmod 600 .env.gcp
fi

# Keep the VM IP in CORS so the site works before DNS/SSL.
grep -q "${EXTERNAL_IP}" .env.gcp || sed -i "s|^CORS_ALLOWED_ORIGINS=.*|&,http://${EXTERNAL_IP}|" .env.gcp

docker compose --env-file .env.gcp -f docker-compose.yml -f docker-compose.gcp.yml up -d --build

echo
echo "Catalog Studio is starting at ${PUBLIC_ORIGIN}"
echo "First image build can take 10–20 minutes on e2-medium."
echo "Check: docker compose -f docker-compose.yml -f docker-compose.gcp.yml ps"
