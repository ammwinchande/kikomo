package co.tz.kikomo.shared;

import com.fasterxml.jackson.annotation.JsonValue;

/** Outcome of {@code Evaluate}; stored in {@code calls.decision}. */
public enum Decision {
    ALLOW("allow"),
    DENY("deny"),
    NEEDS_APPROVAL("needs_approval");

    private final String value;

    Decision(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public static Decision fromValue(String value) {
        for (Decision d : values()) {
            if (d.value.equals(value)) return d;
        }
        throw new IllegalArgumentException("Unknown decision: " + value);
    }
}
