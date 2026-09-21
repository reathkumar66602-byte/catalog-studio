-- Payment WhatsApp number used on the subscription page. Override with
-- catalogstudio.billing.whatsapp-number / BILLING_WHATSAPP_NUMBER.
UPDATE billing_settings
SET
    whatsapp_number = '919560111849',
    updated_at = NOW()
WHERE whatsapp_number IN ('919876543210', '9876543210', '917290942427', '7290942427', '9560111849');

ALTER TABLE billing_settings
    ALTER COLUMN whatsapp_number SET DEFAULT '919560111849';
