package co.tz.kikomo.shared.error;

import java.util.List;

/** A policy document violates SPEC 6.1 (HTTP 400). */
public class PolicyValidationException extends RuntimeException {
    private final List<String> errors;

    public PolicyValidationException(List<String> errors) {
        super("Invalid policy: " + String.join("; ", errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }
}
