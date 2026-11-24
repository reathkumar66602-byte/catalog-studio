package com.catalogstudio.wholesale.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Converts INR amounts to Indian English words for tax invoices. */
final class IndianAmountInWords {

    private static final String[] ONES = {
            "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
            "Seventeen", "Eighteen", "Nineteen"
    };
    private static final String[] TENS = {
            "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private IndianAmountInWords() {}

    static String toWords(BigDecimal amount) {
        if (amount == null) {
            return "Zero";
        }
        BigDecimal rounded = amount.setScale(2, RoundingMode.HALF_UP).abs();
        long rupees = rounded.longValue();
        int paise = rounded.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        String words = convert(rupees);
        if (paise > 0) {
            words = words + " and " + convert(paise) + " Paise";
        }
        return words;
    }

    private static String convert(long n) {
        if (n == 0) {
            return "Zero";
        }
        StringBuilder sb = new StringBuilder();
        long crore = n / 10_000_000;
        n %= 10_000_000;
        long lakh = n / 100_000;
        n %= 100_000;
        long thousand = n / 1_000;
        n %= 1_000;
        long hundred = n / 100;
        n %= 100;
        if (crore > 0) {
            sb.append(twoDigits(crore)).append(" Crore ");
        }
        if (lakh > 0) {
            sb.append(twoDigits(lakh)).append(" Lakh ");
        }
        if (thousand > 0) {
            sb.append(twoDigits(thousand)).append(" Thousand ");
        }
        if (hundred > 0) {
            sb.append(ONES[(int) hundred]).append(" Hundred ");
        }
        if (n > 0) {
            sb.append(twoDigits(n));
        }
        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    private static String twoDigits(long n) {
        if (n < 20) {
            return ONES[(int) n];
        }
        int tens = (int) (n / 10);
        int ones = (int) (n % 10);
        if (ones == 0) {
            return TENS[tens];
        }
        return TENS[tens] + " " + ONES[ones];
    }
}
