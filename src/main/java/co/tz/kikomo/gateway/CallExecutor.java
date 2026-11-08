package co.tz.kikomo.gateway;

import co.tz.kikomo.audit.AuditEvent;
import co.tz.kikomo.audit.AuditService;
import co.tz.kikomo.db.CallRecord;
import co.tz.kikomo.db.CallRepository;
import co.tz.kikomo.db.LedgerRepository;
import co.tz.kikomo.shared.CallStatus;
import co.tz.kikomo.shared.Tools;
import co.tz.kikomo.shared.error.NotFoundException;
import co.tz.kikomo.shared.error.ToolExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

/**
 * Executes a call that is in status {@code executing} and records the outcome. Only the PayPal/fetch side
 * effect is inside the failure handler: if recording fails after PayPal succeeded, the call is NOT reported
 * as failed (money moved) and stays {@code executing}, which keeps its amount reserved against the budget.
 */
@Component
public class CallExecutor {
    private static final Logger log = LoggerFactory.getLogger(CallExecutor.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };
    private static final String GENERIC_FAILURE = "Internal error while executing the tool";
    private static final String KEY_CALL_ID = "call_id";
    private static final String KEY_TOOL = "tool";

    private final CallRepository calls;
    private final LedgerRepository ledger;
    private final ToolExecutor tools;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final JsonMapper json;

    public CallExecutor(CallRepository calls, LedgerRepository ledger, ToolExecutor tools, AuditService audit,
                        TransactionTemplate tx, JsonMapper json) {
        this.calls = calls;
        this.ledger = ledger;
        this.tools = tools;
        this.audit = audit;
        this.tx = tx;
        this.json = json;
    }

    public ToolEnvelope execute(UUID callId) {
        CallRecord call = calls.findById(callId).orElseThrow(() -> new NotFoundException("call " + callId + " not found"));
        if (call.status() != CallStatus.EXECUTING) {
            return envelopeFor(call); // already executed by someone else
        }
        Map<String, Object> result;
        try {
            result = tools.run(call);
        } catch (ToolExecutionException e) {
            log.warn("Tool {} failed for call {}: {}", call.tool(), callId, e.getMessage());
            return fail(call, e.getMessage());
        } catch (RuntimeException e) {
            log.error("Unexpected error executing tool {} for call {}", call.tool(), callId, e);
            return fail(call, GENERIC_FAILURE);
        }
        recordSuccess(call, result);
        return ToolEnvelope.of(calls.findById(callId).orElse(call), result);
    }

    public ToolEnvelope envelopeFor(CallRecord call) {
        Map<String, Object> result = call.resultJson() == null ? null : json.readValue(call.resultJson(), MAP);
        return ToolEnvelope.of(call, result);
    }
    
    private void recordSuccess(CallRecord call, Map<String, Object> result) {
        try {
            tx.executeWithoutResult(status -> {
                calls.complete(call.id(), CallStatus.EXECUTED, json.writeValueAsString(result));
                if (Tools.isSpending(call.tool()) && call.amountMinor() != null) {
                    ledger.record(call.agentId(), call.id(), call.amountMinor(), call.currency(), call.counterparty());
                }
            });
            log.info("Call {} executed: tool={} agent={}", call.id(), call.tool(), call.agentId());
            audit.appendQuietly(call.agentId(), AuditEvent.CALL_EXECUTED, Map.of(KEY_CALL_ID, call.id().toString(), KEY_TOOL, call.tool()));
        } catch (DataAccessException e) {
            log.error("CRITICAL: tool {} succeeded for call {} but the outcome could not be recorded; call left 'executing' "
                    + "(amount stays reserved). PayPal result: {}", call.tool(), call.id(), result, e);
            audit.appendQuietly(call.agentId(), AuditEvent.CALL_RECORDING_FAILED, Map.of(KEY_CALL_ID, call.id().toString(), KEY_TOOL, call.tool()));
        }
    }

    private ToolEnvelope fail(CallRecord call, String message) {
        Map<String, Object> error = Map.of(ToolEnvelope.RESULT_ERROR, message);
        calls.complete(call.id(), CallStatus.FAILED, json.writeValueAsString(error));
        audit.appendQuietly(call.agentId(), AuditEvent.CALL_FAILED,
                Map.of(KEY_CALL_ID, call.id().toString(), KEY_TOOL, call.tool(), ToolEnvelope.RESULT_ERROR, message));
        return ToolEnvelope.of(calls.findById(call.id()).orElse(call), error);
    }
}
