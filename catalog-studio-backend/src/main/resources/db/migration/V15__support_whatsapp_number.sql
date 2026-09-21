-- Replace the placeholder WhatsApp number. Custom admin numbers are left unchanged.
UPDATE billing_settings
SET
    whatsapp_number = '917290942427',
    updated_at = NOW()
WHERE whatsapp_number IN ('919876543210', '9876543210');

ALTER TABLE billing_settings
    ALTER COLUMN whatsapp_number SET DEFAULT '917290942427';
