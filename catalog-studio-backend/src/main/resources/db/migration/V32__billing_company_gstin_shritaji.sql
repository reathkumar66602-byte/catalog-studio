-- Seed company GSTIN, parent company spelling (Shritaji), and standard tax defaults.
-- GST 18% is the India SaaS / OIDAR standard; service charge stays 0% (tax invoice style).

UPDATE billing_settings
SET
    company_legal_name = COALESCE(NULLIF(TRIM(company_legal_name), ''), 'Catalog Studio'),
    company_gstin = CASE
        WHEN company_gstin IS NULL OR TRIM(company_gstin) = '' THEN '19CMZPM0096H1ZA'
        ELSE company_gstin
    END,
    parent_company_name = CASE
        WHEN parent_company_name IS NULL
          OR TRIM(parent_company_name) = ''
          OR UPPER(TRIM(parent_company_name)) IN ('SHIRTAJI', 'SHIRTAJI ')
          OR LOWER(TRIM(parent_company_name)) = 'shirtaji'
        THEN 'Shritaji'
        ELSE parent_company_name
    END,
    gst_percent = COALESCE(gst_percent, 18),
    service_charge_percent = COALESCE(service_charge_percent, 0),
    updated_at = NOW()
WHERE settings_key = 'default';

-- Normalize any lingering Shirtaji spelling
UPDATE billing_settings
SET parent_company_name = 'Shritaji', updated_at = NOW()
WHERE LOWER(TRIM(parent_company_name)) = 'shirtaji';
