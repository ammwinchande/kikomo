package co.tz.kikomo.shared;

import com.fasterxml.jackson.annotation.JsonValue;

/** Human approval state; stored in {@code approvals.status}. */
public enum ApprovalStatus {
    PENDING("pending"),
    APPROVED("approved"),
    DENIED("denied"),
    EXPIRED("expired");

    private final String value;

    ApprovalStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public static ApprovalStatus fromValue(String value) {
        for (ApprovalStatus s : values()) {
            if (s.value.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown approval status: " + value);
    }
}
