#!/usr/bin/env bash
# Enable HTTPS after GoDaddy A records point catalogstudio.in to this VM.
# Run on the VM: sudo bash /opt/catalog-studio/scripts/gcp-enable-https.sh
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/catalog-studio}"
EMAIL="${LETSENCRYPT_EMAIL:-reathkumar66602@gmail.com}"
DOMAINS="-d catalogstudio.in -d www.catalogstudio.in"

apt-get update -y
apt-get install -y certbot

cd "$APP_DIR"
docker compose --env-file .env.gcp -f docker-compose.yml -f docker-compose.gcp.yml stop dashboard

certbot certonly --standalone --non-interactive --agree-tos --email "$EMAIL" $DOMAINS

mkdir -p "$APP_DIR/certs"
cp /etc/letsencrypt/live/catalogstudio.in/fullchain.pem "$APP_DIR/certs/fullchain.pem"
cp /etc/letsencrypt/live/catalogstudio.in/privkey.pem "$APP_DIR/certs/privkey.pem"
chmod 644 "$APP_DIR/certs/"*.pem

cat > "$APP_DIR/catalog-studio-dashboard/nginx.conf" <<'NGINX'
server {
    listen 80;
    server_name catalogstudio.in www.catalogstudio.in;
    location /.well-known/acme-challenge/ {
        root /var/www/certbot;
    }
    location / {
        return 301 https://$host$request_uri;
    }
}

server {
    listen 443 ssl;
    server_name catalogstudio.in www.catalogstudio.in;
    ssl_certificate /etc/nginx/certs/fullchain.pem;
    ssl_certificate_key /etc/nginx/certs/privkey.pem;
    root /usr/share/nginx/html;
    index index.html;
    client_max_body_size 50m;

    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
    }

    location /swagger-ui {
        proxy_pass http://backend:8080/swagger-ui;
    }

    location /v3/api-docs {
        proxy_pass http://backend:8080/v3/api-docs;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
NGINX

docker compose --env-file .env.gcp -f docker-compose.yml -f docker-compose.gcp.yml up -d --build dashboard
echo "HTTPS should be live at https://www.catalogstudio.in"
