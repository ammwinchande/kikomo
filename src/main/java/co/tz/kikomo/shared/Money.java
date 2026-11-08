package co.tz.kikomo.shared;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * Money as {@code long} minor units (cents). Decimal strings are parsed with a strict, bounded regex.
 */
public final class Money {

    public static final String DEFAULT_CURRENCY = "USD";

    /**
     * At most 13 whole digits (USD 9,999,999,999,999.99) so {@code whole * 100} can never overflow a long.
     * Anything larger is an invalid format, not a silently wrapped (negative) amount.
     */
    private static final Pattern AMOUNT = Pattern.compile("^(0|[1-9]\\d{0,12})(\\.\\d{1,2})?$");

    private Money() {
        /* utility class */
    }

    public static boolean isValidFormat(String raw) {
        return raw != null && AMOUNT.matcher(raw.trim()).matches();
    }

    /**
     * @throws IllegalArgumentException if {@code raw} is not a valid amount
     */
    public static long parseMinor(String raw) {
        if (!isValidFormat(raw)) {
            throw new IllegalArgumentException("INVALID_AMOUNT_FORMAT");
        }
        String trimmed = raw.trim();
        int dot = trimmed.indexOf('.');
        if (dot < 0) {
            return Math.multiplyExact(Long.parseLong(trimmed), 100L);
        }
        long whole = Long.parseLong(trimmed.substring(0, dot));
        String fraction = trimmed.substring(dot + 1);
        if (fraction.length() == 1) {
            fraction += "0";
        }
        return Math.addExact(Math.multiplyExact(whole, 100L), Long.parseLong(fraction));
    }

    /**
     * Locale-independent decimal string, e.g. {@code 1250 -> "12.50"}.
     */
    public static String formatMinor(long minor) {
        return BigDecimal.valueOf(minor, 2).toPlainString();
    }
}
