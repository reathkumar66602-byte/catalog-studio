#!/bin/bash
set -euo pipefail
echo "=== UPDATE ==="
sudo docker exec catalog-studio-postgres-1 psql -U catalog_studio -d catalog_studio -c "UPDATE clients SET phone = '+917003070165', updated_at = NOW() WHERE slug = 'krishna-store';"
echo "=== RESULT ==="
sudo docker exec catalog-studio-postgres-1 psql -U catalog_studio -d catalog_studio -c "SELECT store_name, phone FROM clients WHERE slug = 'krishna-store';"
