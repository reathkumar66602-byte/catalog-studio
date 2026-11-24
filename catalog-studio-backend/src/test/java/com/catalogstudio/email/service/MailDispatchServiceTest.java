package com.catalogstudio.email.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MailDispatchServiceTest {

    @Test
    void preferZeptoForOtpPlanAndCampaign() {
        assertTrue(MailDispatchService.preferZeptoFor("otp-register"));
        assertTrue(MailDispatchService.preferZeptoFor("otp-login"));
        assertTrue(MailDispatchService.preferZeptoFor("otp-verify-email"));
        assertTrue(MailDispatchService.preferZeptoFor("plan-activated"));
        assertTrue(MailDispatchService.preferZeptoFor("plan-expired"));
        assertTrue(MailDispatchService.preferZeptoFor("campaign"));
        assertTrue(MailDispatchService.preferZeptoFor("campaign-weekly"));
    }

    @Test
    void preferZeptoSkipsEnquiryAndGeneric() {
        assertFalse(MailDispatchService.preferZeptoFor(null));
        assertFalse(MailDispatchService.preferZeptoFor(""));
        assertFalse(MailDispatchService.preferZeptoFor("enquiry"));
        assertFalse(MailDispatchService.preferZeptoFor("enquiry-ack"));
        assertFalse(MailDispatchService.preferZeptoFor("catalog-studio"));
        assertFalse(MailDispatchService.preferZeptoFor("password-reset"));
        assertFalse(MailDispatchService.preferZeptoFor("wholesale-bill"));
    }
}
