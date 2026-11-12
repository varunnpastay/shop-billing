package com.smme.billing.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Splits an amount into the "Rs" and "Ps" columns of the printed bill, with Indian digit grouping (20,790 / 1,20,790).
 * Used from templates as {@code ${@money.rs(x)}} and {@code ${@money.ps(x)}}.
 */
@Component("money")
public class Money {

    public String rs(BigDecimal v) {
        if (v == null) return "";
        BigDecimal a = v.setScale(2, RoundingMode.HALF_UP);
        String sign = a.signum() < 0 ? "-" : "";
        return sign + group(a.abs().toBigInteger().toString());
    }

    public String ps(BigDecimal v) {
        if (v == null) return "";
        int paise = v.setScale(2, RoundingMode.HALF_UP).abs().remainder(BigDecimal.ONE).movePointRight(2).intValue();
        return String.format("%02d", paise);
    }

    /** 120790.5 -> "1,20,790.50" (null -> "0.00"). */
    public String inr(BigDecimal v) {
        BigDecimal a = (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
        return rs(a) + "." + ps(a);
    }

    /** Whole numbers without trailing zeros: 110.000 -> "110", 2.5 -> "2.5". */
    public String qty(BigDecimal v) {
        return v == null ? "" : v.stripTrailingZeros().toPlainString();
    }

    /** 1234567 -> 12,34,567 */
    static String group(String digits) {
        if (digits.length() <= 3) return digits;
        String last3 = digits.substring(digits.length() - 3);
        String rest = digits.substring(0, digits.length() - 3);
        StringBuilder sb = new StringBuilder();
        while (rest.length() > 2) {
            sb.insert(0, "," + rest.substring(rest.length() - 2));
            rest = rest.substring(0, rest.length() - 2);
        }
        sb.insert(0, rest);
        return sb + "," + last3;
    }
}
