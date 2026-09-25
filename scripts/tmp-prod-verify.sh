#!/bin/bash
set -euo pipefail
sleep 40
echo "=== START ==="
sudo docker logs catalog-studio-backend-1 2>&1 | grep -E 'Migrating schema|SMTP ready|Started Catalog|APPLICATION FAILED|Schema-validation|now at version' | tail -n 25
echo "=== HTTP ==="
curl -sk -o /dev/null -w "https=%{http_code}\n" https://127.0.0.1/
curl -sk -o /dev/null -w "site=%{http_code}\n" https://127.0.0.1/api/v1/site
echo "=== JS ==="
html=$(curl -sk https://127.0.0.1/)
js=$(printf '%s\n' "$html" | grep -oE '/assets/index-[^"]+\.js' | head -n 1)
echo "bundle=$js"
# Meesho Shoot background picker (Auto default + fixed scenes)
curl -sk "https://127.0.0.1$js" | grep -o 'FESTIVE_HOME' | head -n 1 || true
curl -sk "https://127.0.0.1$js" | grep -o 'meeshoBackground' | head -n 1 || true
curl -sk "https://127.0.0.1$js" | grep -o 'Festive home' | head -n 1 || true
sudo rm -f /tmp/catalog-studio-src.tgz /tmp/tmp-prod-deploy.sh /tmp/tmp-prod-verify.sh
echo "=== CLEANED ==="
