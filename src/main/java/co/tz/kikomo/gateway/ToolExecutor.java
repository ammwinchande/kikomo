package co.tz.kikomo.gateway;

import co.tz.kikomo.db.ApprovalRepository;
import co.tz.kikomo.db.CallRecord;
import co.tz.kikomo.db.CallRepository;
import co.tz.kikomo.paypal.PayPalClient;
import co.tz.kikomo.shared.Tools;
import co.tz.kikomo.shared.error.ToolExecutionException;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Runs the side effect of one recorded call (PayPal, fetch or approval lookup).
 */
@Component
public class ToolExecutor {
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    /**
     * PayPal order/capture ids; anything else could rewrite the request path (e.g. "../").
     */
    private static final Pattern PAYPAL_ID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");
    private static final String KEY_STATUS = "status";
    private static final String DEFAULT_NOTE = "Kikomo payment";

    private final PayPalClient paypal;
    private final UrlFetcher fetcher;
    private final ApprovalRepository approvals;
    private final CallRepository calls;
    private final JsonMapper json;

    public ToolExecutor(PayPalClient paypal, UrlFetcher fetcher, ApprovalRepository approvals, CallRepository calls, JsonMapper json) {
        this.paypal = paypal;
        this.fetcher = fetcher;
        this.approvals = approvals;
        this.calls = calls;
        this.json = json;
    }

    private static long requireAmount(CallRecord call) {
        if (call.amountMinor() == null) throw new ToolExecutionException("amount is required for " + call.tool());
        return call.amountMinor();
    }

    private static String requirePayPalId(ToolRequest request, String key) {
        String value = request.string(key);
        if (value == null || !PAYPAL_ID.matcher(value).matches()) {
            throw new ToolExecutionException(key + " is missing or malformed");
        }
        return value;
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * @throws ToolExecutionException with an agent-safe message when the tool cannot run
     */
    public Map<String, Object> run(CallRecord call) {
        ToolRequest request = new ToolRequest(call.tool(), json.readValue(call.argsJson(), MAP));
        String idempotencyKey = call.id().toString();
        return switch (call.tool()) {
            case Tools.SEND_PAYMENT ->
                    paypal.sendPayment(idempotencyKey, call.counterparty(), requireAmount(call), call.currency(),
                            Optional.ofNullable(request.string(ToolRequest.ARG_NOTE)).orElse(DEFAULT_NOTE));
            case Tools.CREATE_ORDER ->
                    paypal.createOrder(idempotencyKey, requireAmount(call), call.currency(), call.counterparty());
            case Tools.GET_ORDER -> paypal.getOrder(requirePayPalId(request, ToolRequest.ARG_ORDER_ID));
            case Tools.CREATE_INVOICE ->
                    paypal.createInvoice(idempotencyKey, call.counterparty(), requireAmount(call), call.currency());
            case Tools.CREATE_REFUND ->
                    paypal.createRefund(idempotencyKey, requirePayPalId(request, ToolRequest.ARG_CAPTURE_ID),
                            requireAmount(call), call.currency());
            case Tools.LIST_TRANSACTIONS -> paypal.listTransactions();
            case Tools.CHECK_APPROVAL -> checkApproval(request);
            case Tools.FETCH_URL -> fetcher.fetchAndRecord(call.agentId(), request.string(ToolRequest.ARG_URL));
            default -> throw new ToolExecutionException("Unknown tool: " + call.tool());
        };
    }

    /**
     * Status of an approval (by approval_id) or of a call (by call_id).
     */
    Map<String, Object> checkApproval(ToolRequest request) {
        String id = Optional.ofNullable(request.string(ToolRequest.ARG_APPROVAL_ID)).orElse(request.string(ToolRequest.ARG_CALL_ID));
        if (id == null) return Map.of(KEY_STATUS, "unknown");
        UUID uuid = parseUuid(id);
        if (uuid == null) return Map.of(KEY_STATUS, "not_found");
        return approvals.findById(uuid)
                .map(a -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put(ToolRequest.ARG_APPROVAL_ID, id);
                    out.put(KEY_STATUS, a.status().value());
                    out.put(ToolRequest.ARG_CALL_ID, a.callId().toString());
                    calls.findById(a.callId()).ifPresent(c -> {
                        out.put("call_status", c.status().value());
                        if (c.resultJson() != null) out.put("result", json.readValue(c.resultJson(), MAP));
                    });
                    return out;
                })
                .or(() -> calls.findById(uuid).map(c -> Map.of(
                        ToolRequest.ARG_CALL_ID, id, KEY_STATUS, c.status().value(), "decision", c.decision().value())))
                .orElse(Map.of(KEY_STATUS, "not_found"));
    }
}
