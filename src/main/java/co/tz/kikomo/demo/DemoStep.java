package co.tz.kikomo.demo;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/**
 * One line of the demo feed. {@code kind} is {@code info}, {@code allow}, {@code approve} or {@code deny};
 * {@code label} is the real reason code or call status.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DemoStep(String id, String kind, String title, String label, String detail, String callId, String approvalId) {
    public static final String INFO = "info";
    public static final String ALLOW = "allow";
    public static final String APPROVE = "approve";
    public static final String DENY = "deny";

    static DemoStep info(String id, String title, String label, String detail) {
        return new DemoStep(id, INFO, title, label, detail, null, null);
    }
}
