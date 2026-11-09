package co.tz.kikomo.web.dto;

import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Dashboard tiles. "Today" uses the database day boundary, the same one the daily budget uses. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record StatsView(long pendingApprovals, long allowedToday, long deniedToday, long needsApprovalToday, boolean auditValid) {
}
