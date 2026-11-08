package co.tz.kikomo.shared;

import com.fasterxml.jackson.annotation.JsonValue;

/** Lifecycle of a gateway call; stored in {@code calls.status}. */
public enum CallStatus {
    /** Allowed (or approved) and currently being executed against PayPal; counts as reserved spend. */
    EXECUTING("executing"),
    EXECUTED("executed"),
    DENIED("denied"),
    PENDING_APPROVAL("pending_approval"),
    FAILED("failed"),
    EXPIRED("expired");

    private final String value;

    CallStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public boolean isFinal() {
        return this == EXECUTED || this == DENIED || this == FAILED || this == EXPIRED;
    }

    public static CallStatus fromValue(String value) {
        for (CallStatus s : values()) {
            if (s.value.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown call status: " + value);
    }
}
