#!/bin/bash
set -euo pipefail
APP=/opt/catalog-studio
TAR=/tmp/catalog-studio-src.tgz

if [[ ! -f "$APP/.env.gcp" ]]; then
  echo "Missing $APP/.env.gcp — aborting so mail secrets are not lost."
  exit 1
fi
if [[ ! -f "$TAR" ]]; then
  echo "Missing $TAR"
  exit 1
fi

sudo rm -rf "$APP/catalog-studio-backend/src" "$APP/catalog-studio-dashboard/src"
sudo tar -xzf "$TAR" -C "$APP"

sudo tee "$APP/catalog-studio-dashboard/nginx.conf" >/dev/null <<'NGINX'
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

cd "$APP"
echo "=== BUILD START $(date -u +%Y-%m-%dT%H:%M:%SZ) ==="
sudo docker compose --env-file .env.gcp -f docker-compose.yml -f docker-compose.gcp.yml up -d --build backend dashboard
echo "=== COMPOSE PS ==="
sudo docker compose --env-file .env.gcp -f docker-compose.yml -f docker-compose.gcp.yml ps
echo "=== BUILD DONE $(date -u +%Y-%m-%dT%H:%M:%SZ) ==="
