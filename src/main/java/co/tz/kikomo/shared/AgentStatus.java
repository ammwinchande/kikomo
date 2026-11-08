package co.tz.kikomo.shared;

import com.fasterxml.jackson.annotation.JsonValue;

/** Kill-switch state of an agent; stored in {@code agents.status}. */
public enum AgentStatus {
    ACTIVE("active"),
    SUSPENDED("suspended");

    private final String value;

    AgentStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public static AgentStatus fromValue(String value) {
        for (AgentStatus s : values()) {
            if (s.value.equals(value)) return s;
        }
        throw new IllegalArgumentException("Unknown agent status: " + value);
    }
}
