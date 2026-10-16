sleep 35
echo VERIFY_LOGS
sudo docker logs catalog-studio-backend-1 --tail 120 2>&1 | grep -iE 'SMTP ready|Zepto|MailConfiguration|Started Catalog|FAILED|Migrating schema|now at version|enquiry reply' | tail -n 30
echo MAIL_ENV
sudo grep -E '^(MAIL_PROVIDER|ZOHO_MAIL_USERNAME|ZEPTOMAIL_SEND_TOKEN)=' /opt/catalog-studio/.env.gcp | sed -E 's/(TOKEN)=.+/\1=SET/; s/(TOKEN)=$/\1=EMPTY/'
echo FLYWAY
sudo docker exec catalog-studio-postgres-1 psql -U catalog_studio -d catalog_studio -c "SELECT version, success FROM flyway_schema_history WHERE version::int >= 32 ORDER BY installed_rank;"
echo HTTP
curl -sk -o /dev/null -w "site=%{http_code}\n" https://127.0.0.1/api/v1/site