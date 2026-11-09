package co.tz.kikomo.web.dto;

import co.tz.kikomo.db.Approval;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** {@code expires_at} is ISO-8601 with offset, e.g. {@code 2026-11-08T07:15:00Z}. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ApprovalView(String id, String callId, String agentId, String status, String expiresAt) {
    public static ApprovalView of(Approval approval) {
        return new ApprovalView(approval.id().toString(), approval.callId().toString(), approval.agentId().toString(),
                approval.status().value(), approval.expiresAt().toInstant().toString());
    }
}
