package com.catalogstudio.campaign.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CampaignBillingErrorsTest {

    @Test
    void detectsCommonBillingMessages() {
        assertTrue(CampaignBillingErrors.isBillingOrQuotaError("Insufficient credit in wallet"));
        assertTrue(CampaignBillingErrors.isBillingOrQuotaError("ZeptoMail HTTP 402: payment required"));
        assertTrue(CampaignBillingErrors.isBillingOrQuotaError("WhatsApp provider returned HTTP 400: quota exceeded"));
        assertTrue(CampaignBillingErrors.isBillingOrQuotaError("Please top-up your account"));
    }

    @Test
    void ignoresOrdinaryDeliveryFailures() {
        assertFalse(CampaignBillingErrors.isBillingOrQuotaError(null));
        assertFalse(CampaignBillingErrors.isBillingOrQuotaError(""));
        assertFalse(CampaignBillingErrors.isBillingOrQuotaError("User has no email"));
        assertFalse(CampaignBillingErrors.isBillingOrQuotaError("WhatsApp provider returned HTTP 500: upstream timeout"));
        assertFalse(CampaignBillingErrors.isBillingOrQuotaError("Invalid template key"));
    }
}
