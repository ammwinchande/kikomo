package co.tz.kikomo.gateway;

import co.tz.kikomo.db.CallRecord;
import co.tz.kikomo.engine.Explain;
import co.tz.kikomo.shared.Reason;
import co.tz.kikomo.shared.Tools;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Response of every gateway tool (SPEC section 7):
 * {@code {status, call_id, decision, reason_codes, explanation, paypal}} plus {@code approval_id} while a call
 * waits for a human, {@code error} when execution failed, and {@code fetched_url}/{@code emails_found} for
 * kikomo_fetch_url.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonPropertyOrder({"status", "call_id", "decision", "reason_codes", "explanation", "approval_id", "paypal", "fetched_url", "emails_found", "error"})
public record ToolEnvelope(String status, String callId, String decision, List<String> reasonCodes, String explanation,
                           String approvalId, Map<String, Object> paypal, String fetchedUrl, List<String> emailsFound,
                           String error) {

    static final String RESULT_ERROR = "error";
    static final String RESULT_FETCHED_URL = "fetched_url";
    static final String RESULT_EMAILS_FOUND = "emails_found";

    /** Builds the envelope for a call and its stored result ({@code calls.result}). */
    @SuppressWarnings("unchecked")
    public static ToolEnvelope of(CallRecord call, Map<String, Object> result) {
        Map<String, Object> safeResult = result == null ? Map.of() : result;
        String error = safeResult.containsKey(RESULT_ERROR) ? String.valueOf(safeResult.get(RESULT_ERROR)) : null;
        boolean hasPayload = result != null && error == null;
        boolean fetch = Tools.isFetch(call.tool());
        return new ToolEnvelope(
                call.status().value(),
                call.id().toString(),
                call.decision().value(),
                call.reasonCodes(),
                Explain.explain(call.reasonCodes().stream().map(Reason::valueOf).toList()),
                idOrNull(call.approvalId()),
                hasPayload && !fetch ? result : null,
                hasPayload && fetch ? (String) safeResult.get(RESULT_FETCHED_URL) : null,
                hasPayload && fetch ? (List<String>) safeResult.get(RESULT_EMAILS_FOUND) : null,
                error);
    }

    private static String idOrNull(UUID id) {
        return id == null ? null : id.toString();
    }
}
