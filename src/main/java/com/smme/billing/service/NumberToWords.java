package com.smme.billing.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class NumberToWords {

    private static final String[] ONES = {"", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
            "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS = {"", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"};

    public static String convert(BigDecimal amount) {
        if (amount == null) return "";
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() < 0) return "Minus " + convert(amount.negate());
        long rupees = amount.longValue();
        int paise = amount.remainder(BigDecimal.ONE).movePointRight(2).abs().intValue();

        String words = rupees == 0 ? "Zero" : toWords(rupees);
        StringBuilder sb = new StringBuilder("Rupees ").append(words);
        if (paise > 0) sb.append(" and ").append(toWords(paise)).append(" Paise");
        return sb.append(" Only").toString();
    }

    private static String toWords(long n) {
        StringBuilder sb = new StringBuilder();
        if (n >= 10000000) { sb.append(toWords(n / 10000000)).append(" Crore "); n %= 10000000; }
        if (n >= 100000) { sb.append(twoDigits((int) (n / 100000))).append(" Lakh "); n %= 100000; }
        if (n >= 1000) { sb.append(twoDigits((int) (n / 1000))).append(" Thousand "); n %= 1000; }
        if (n >= 100) { sb.append(ONES[(int) (n / 100)]).append(" Hundred "); n %= 100; }
        if (n > 0) sb.append(twoDigits((int) n));
        return sb.toString().trim().replaceAll("\\s+", " ");
    }

    private static String twoDigits(int n) {
        if (n < 20) return ONES[n];
        return (TENS[n / 10] + " " + ONES[n % 10]).trim();
    }
}
