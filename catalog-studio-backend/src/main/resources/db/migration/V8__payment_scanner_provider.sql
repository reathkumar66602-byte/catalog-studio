-- Scanner image, payee label, and a payment-provider switch for later Razorpay/other gateways.

ALTER TABLE billing_settings
    ADD COLUMN IF NOT EXISTS payment_provider VARCHAR(40) NOT NULL DEFAULT 'MANUAL';

UPDATE billing_settings
SET
    qr_image_url = COALESCE(NULLIF(btrim(qr_image_url), ''), '/payment-qr.jpg'),
    payee_name = CASE
        WHEN payee_name IS NULL OR btrim(payee_name) = '' OR payee_name = 'Catalog Studio'
            THEN 'VISHAL KUMAR MISHRA'
        ELSE payee_name
    END,
    payment_provider = COALESCE(NULLIF(btrim(payment_provider), ''), 'MANUAL'),
    payment_instructions = CASE
        WHEN payment_instructions ILIKE '%Scan the UPI QR%'
            THEN 'Scan this PhonePe QR, pay the plan amount, then send the payment screenshot on WhatsApp. Mention your registered email ID in the same message so we can activate the correct account.'
        ELSE payment_instructions
    END,
    updated_at = NOW()
WHERE settings_key = 'default';
