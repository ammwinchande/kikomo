package co.tz.kikomo.web.dto;

import co.tz.kikomo.db.CallRecord;
import co.tz.kikomo.engine.Explain;
import co.tz.kikomo.shared.Reason;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.UUID;

/**
 * Row of the inbox call list; every key is always present so the SPA sees a stable shape.
 * {@code args} and {@code result} are the stored JSON documents, emitted verbatim.
 * {@code explanation} is the deterministic plain-words text for the reason codes (never AI).
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CallView(String id, String agentId, String tool, String decision, String status, String counterparty,
                       Long amountMinor, String currency, String category, List<String> reasonCodes, String explanation,
                       @JsonRawValue String args, @JsonRawValue String result, String approvalId, String createdAt) {
    public static CallView of(CallRecord call) {
        return new CallView(call.id().toString(), call.agentId().toString(), call.tool(), call.decision().value(),
                call.status().value(), call.counterparty(), call.amountMinor(), call.currency(), call.category(),
                call.reasonCodes(), explanation(call.reasonCodes()), call.argsJson(), call.resultJson(),
                idOrNull(call.approvalId()), call.createdAt() == null ? null : call.createdAt().toInstant().toString());
    }

    static String explanation(List<String> codes) {
        List<Reason> known = codes.stream().filter(CallView::isKnown).map(Reason::valueOf).toList();
        return Explain.explain(known);
    }

    private static boolean isKnown(String code) {
        try {
            Reason.valueOf(code);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String idOrNull(UUID id) {
        return id == null ? null : id.toString();
    }
}
