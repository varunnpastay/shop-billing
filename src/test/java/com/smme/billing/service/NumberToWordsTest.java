package com.smme.billing.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NumberToWordsTest {

    private static String w(String v) {
        return NumberToWords.convert(new BigDecimal(v));
    }

    @Test
    void convertsIndianNumbering() {
        assertEquals("Rupees Zero Only", w("0"));
        assertEquals("Rupees One Only", w("1"));
        assertEquals("Rupees Nineteen Only", w("19"));
        assertEquals("Rupees One Hundred Five Only", w("105"));
        assertEquals("Rupees Twelve Thousand Three Hundred Forty Five Only", w("12345"));
        assertEquals("Rupees One Lakh Only", w("100000"));
        assertEquals("Rupees Two Crore Fifty Lakh Only", w("25000000"));
        assertEquals("Rupees Ten and Fifty Paise Only", w("10.50"));
    }

    @Test
    void handlesNegativeAndNull() {
        assertEquals("Minus Rupees Five Only", w("-5"));
        assertEquals("", NumberToWords.convert(null));
    }
}
