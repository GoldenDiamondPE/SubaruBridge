package org.mlpi.pixelprobe.ownership;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/** Canonical millimetres, millilitres and minor currency units; never floating-point money. */
public final class OwnershipNumbers {
    private OwnershipNumbers() {}
    public static long scaled(String text, String scale, String label) {
        String value = text.trim();
        if (!value.matches("[0-9]{1,12}(\\.[0-9]{1,6})?")) throw new IllegalArgumentException("Enter " + label + " as a positive number without commas");
        try { return new BigDecimal(value).multiply(new BigDecimal(scale)).setScale(0, RoundingMode.HALF_UP).longValueExact(); }
        catch (ArithmeticException error) { throw new IllegalArgumentException(label + " is too large"); }
    }
    public static long distance(String text, boolean metric) { return scaled(text, metric ? "1000000" : "1609344", "odometer or distance"); }
    public static long volume(String text, boolean metric) { return scaled(text, metric ? "1000" : "3785.411784", "fuel volume"); }
    public static long cents(String text) {
        if (text.trim().isEmpty()) return 0;
        if (!text.trim().matches("[0-9]{1,10}(\\.[0-9]{1,2})?")) throw new IllegalArgumentException("Enter an amount with at most two decimal places");
        return new BigDecimal(text.trim()).movePointRight(2).longValueExact();
    }
    public static String distance(long mm, boolean metric) {
        return BigDecimal.valueOf(mm).divide(new BigDecimal(metric ? "1000000" : "1609344"), 1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
    public static String money(long cents, String currency) {
        try { NumberFormat format = NumberFormat.getCurrencyInstance(Locale.getDefault()); format.setCurrency(Currency.getInstance(currency)); return format.format(BigDecimal.valueOf(cents, 2)); }
        catch (IllegalArgumentException error) { return currency + " " + BigDecimal.valueOf(cents, 2).toPlainString(); }
    }
}
