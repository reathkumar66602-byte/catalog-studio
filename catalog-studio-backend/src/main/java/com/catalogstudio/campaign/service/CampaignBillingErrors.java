package com.catalogstudio.campaign.service;

import java.util.Locale;

/**
 * Detects provider billing / quota failures so campaigns can pause instead of
 * burning through remaining recipients as FAILED.
 */
public final class CampaignBillingErrors {

    private CampaignBillingErrors() {}

    public static boolean isBillingOrQuotaError(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String m = message.toLowerCase(Locale.ROOT);
        return containsAny(
                m,
                "insufficient credit",
                "insufficient funds",
                "insufficient balance",
                "out of credit",
                "no credit",
                "credit exhausted",
                "credits exhausted",
                "not enough credit",
                "not enough credits",
                "prepaid balance",
                "low balance",
                "zero balance",
                "payment required",
                "http 402",
                " status 402",
                "402 payment",
                "quota exceeded",
                "quota exhausted",
                "rate limit exceeded",
                "daily limit",
                "sending limit",
                "wallet empty",
                "top up",
                "top-up",
                "topup required",
                "recharge required",
                "billing issue",
                "account suspended");
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
