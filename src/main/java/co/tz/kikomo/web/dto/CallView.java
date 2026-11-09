package co.tz.kikomo.web.dto;

import co.tz.kikomo.db.CallRecord;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

/**
 * Row of the inbox call list; every key is always present so the SPA sees a stable shape.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CallView(String id, String agentId, String tool, String decision, String status, String counterparty,
                       Long amountMinor, List<String> reasonCodes) {
    public static CallView of(CallRecord call) {
        return new CallView(call.id().toString(), call.agentId().toString(), call.tool(), call.decision().value(),
                call.status().value(), call.counterparty(), call.amountMinor(), call.reasonCodes());
    }
}
