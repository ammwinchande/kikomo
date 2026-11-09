package co.tz.kikomo.web.dto;

import co.tz.kikomo.db.Approval;
import co.tz.kikomo.db.CallRecord;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.time.OffsetDateTime;

/**
 * Approval with the call it guards embedded, so the inbox can show amount, payee and reasons without a second request.
 * Timestamps are ISO-8601 instants, e.g. {@code 2026-11-08T07:15:00Z}.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ApprovalView(String id, String callId, String agentId, String status, String decidedBy, String decidedAt,
                           String expiresAt, String createdAt, CallView call) {
    public static ApprovalView of(Approval approval, CallRecord callOrNull) {
        return new ApprovalView(approval.id().toString(), approval.callId().toString(), approval.agentId().toString(),
                approval.status().value(), approval.decidedBy(), instant(approval.decidedAt()), instant(approval.expiresAt()),
                instant(approval.createdAt()), callOrNull == null ? null : CallView.of(callOrNull));
    }

    private static String instant(OffsetDateTime t) {
        return t == null ? null : t.toInstant().toString();
    }
}
