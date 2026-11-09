package co.tz.kikomo.engine;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Payee normalisation (SPEC 6.4): trim, NFKC, lower-case.
 */
public final class Normalize {
    private static final Pattern EMAIL = Pattern.compile("^[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}$");

    private Normalize() {
        /* utility class */
    }

    /**
     * Uses {@link Locale#ROOT}: with a Turkish default locale {@code "I".toLowerCase()} is a dotless {@code ı},
     * which would make allowlist matching depend on server settings.
     */
    public static String payee(String raw) {
        if (raw == null) {
            return "";
        }
        return Normalizer.normalize(raw, Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isValidEmail(String normalized) {
        return normalized != null && EMAIL.matcher(normalized).matches();
    }
}
