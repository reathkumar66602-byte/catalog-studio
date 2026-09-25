#!/bin/bash
set -euo pipefail
echo "=== FLYWAY ==="
sudo docker logs catalog-studio-backend-1 2>&1 | grep -Ei 'version .2[89]|now at version|Started CatalogStudio|APPLICATION FAILED' | tail -n 20
echo "=== HISTORY ==="
sudo docker exec catalog-studio-postgres-1 psql -U catalog_studio -d catalog_studio -c \
  "SELECT version, description, success FROM flyway_schema_history WHERE version IN ('28','29') ORDER BY installed_rank;"
