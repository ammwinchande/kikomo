package co.tz.kikomo.demo;

import java.util.Locale;

/** The scripted scenarios of the demo runner (SPEC section 14). */
public enum DemoScenario {
    SMALL, LARGE, INJECTION, ALL;

    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static DemoScenario fromValue(String value) {
        if (value == null) throw new IllegalArgumentException("scenario is required: small, large, injection or all");
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown scenario '" + value + "': use small, large, injection or all");
        }
    }
}
