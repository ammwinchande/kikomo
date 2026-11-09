package co.tz.kikomo.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** One {@code audit_log} row. {@code payload} is the stored JSON document, emitted verbatim. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AuditEntryView(long seq, String ts, String agentId, String eventType, @JsonRawValue String payload,
                             String prevHash, String hash) {
}
