package co.tz.kikomo.gateway;

import co.tz.kikomo.audit.AuditEvent;
import co.tz.kikomo.audit.AuditService;
import co.tz.kikomo.config.AppConfig;
import co.tz.kikomo.db.Agent;
import co.tz.kikomo.db.AgentRepository;
import co.tz.kikomo.db.ApprovalRepository;
import co.tz.kikomo.db.CallRecord;
import co.tz.kikomo.db.CallRepository;
import co.tz.kikomo.db.LedgerRepository;
import co.tz.kikomo.db.NewCall;
import co.tz.kikomo.db.PolicyRepository;
import co.tz.kikomo.db.UntrustedRepository;
import co.tz.kikomo.engine.Evaluate;
import co.tz.kikomo.engine.Normalize;
import co.tz.kikomo.shared.AgentStatus;
import co.tz.kikomo.shared.CallStatus;
import co.tz.kikomo.shared.Decision;
import co.tz.kikomo.shared.Policy;
import co.tz.kikomo.shared.Reason;
import co.tz.kikomo.shared.Tools;
import co.tz.kikomo.shared.error.ToolExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Gateway pipeline (SPEC section 8): authenticate (done by the MCP layer), normalise, load state, evaluate,
 * record the call and audit it atomically, then deny / wait for approval / execute.
 */
@Service
public class GatewayManager {
    private static final Logger log = LoggerFactory.getLogger(GatewayManager.class);

    private final AgentRepository agents;
    private final PolicyRepository policies;
    private final CallRepository calls;
    private final ApprovalRepository approvals;
    private final LedgerRepository ledger;
    private final UntrustedRepository untrusted;
    private final UrlFetcher fetcher;
    private final CallExecutor executor;
    private final ApprovalService approvalService;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final JsonMapper json;
    private final AppConfig.Approval approvalConfig;
    private final Clock clock;
    private final Evaluate evaluate = new Evaluate();

    @SuppressWarnings("java:S107") // Spring constructor injection of the pipeline's collaborators
    public GatewayManager(AgentRepository agents, PolicyRepository policies, CallRepository calls, ApprovalRepository approvals,
                          LedgerRepository ledger, UntrustedRepository untrusted, UrlFetcher fetcher, CallExecutor executor,
                          ApprovalService approvalService, AuditService audit, TransactionTemplate tx, JsonMapper json,
                          AppConfig config, Clock clock) {
        this.agents = agents;
        this.policies = policies;
        this.calls = calls;
        this.approvals = approvals;
        this.ledger = ledger;
        this.untrusted = untrusted;
        this.fetcher = fetcher;
        this.executor = executor;
        this.approvalService = approvalService;
        this.audit = audit;
        this.tx = tx;
        this.json = json;
        this.approvalConfig = config.approval();
        this.clock = clock;
    }

    public ToolEnvelope execute(UUID agentId, String tool, Map<String, Object> args) {
        if (!Tools.ALL.contains(tool)) {
            throw new ToolExecutionException("Unknown tool: " + tool);
        }
        ToolRequest request = new ToolRequest(tool, args);
        CallRecord call = tx.execute(status -> evaluateAndRecord(agentId, request));
        if (call == null) {
            throw new IllegalStateException("evaluation transaction returned no call");
        }
        log.info("Call {} evaluated: agent={} tool={} decision={} reasons={}", call.id(), agentId, tool, call.decision().value(), call.reasonCodes());
        return switch (call.decision()) {
            case DENY -> executor.envelopeFor(call);
            case NEEDS_APPROVAL -> awaitDecision(call);
            case ALLOW -> executor.execute(call.id());
        };
    }

    /**
     * Runs under a per-agent advisory lock: concurrent calls of one agent are evaluated one at a time, and an
     * allowed spending call is recorded as {@code executing} (reserved budget) before the lock is released.
     * The call_evaluated audit entry is part of the same transaction, so nothing executes unaudited.
     */
    private CallRecord evaluateAndRecord(UUID agentId, ToolRequest request) {
        agents.lockForEvaluation(agentId);
        AgentStatus agentStatus = agents.findById(agentId).map(Agent::status).orElse(AgentStatus.SUSPENDED);
        Policy policy = policies.findActive(agentId).orElse(null);
        long spentToday = policy == null ? 0 : ledger.spentToday(agentId) + calls.reservedToday(agentId);
        int callsInWindow = policy == null || policy.velocity() == null ? 0 : calls.countInWindow(agentId, policy.velocity().windowSeconds());

        Evaluate.EvaluateResult result = evaluate.evaluate(new Evaluate.EvaluationInput(agentStatus, policy, request.tool(),
                request.amountRaw(), request.currency(), request.counterpartyRaw(), request.category(), spentToday, callsInWindow,
                untrusted.valuesFor(agentId)));
        if (isBlockedFetch(request, result)) {
            result = new Evaluate.EvaluateResult(Decision.DENY, List.of(Reason.SSRF_BLOCKED));
        }

        String counterparty = Normalize.payee(request.counterpartyRaw());
        UUID callId = calls.insert(new NewCall(agentId, request.tool(), json.writeValueAsString(request.args()), request.amountMinor(),
                request.currency(), counterparty.isEmpty() ? null : counterparty, request.category(), result.decision(),
                result.reasonNames(), initialStatus(result.decision())));
        if (result.decision() == Decision.NEEDS_APPROVAL && policy != null) {
            UUID approvalId = approvals.insert(callId, agentId, policy.approvalTtlSeconds());
            calls.attachApproval(callId, approvalId);
        }
        audit.append(agentId, AuditEvent.CALL_EVALUATED, Map.of("call_id", callId.toString(), "tool", request.tool(),
                "decision", result.decision().value(), "reason_codes", result.reasonNames()));
        return calls.findById(callId).orElseThrow();
    }

    private boolean isBlockedFetch(ToolRequest request, Evaluate.EvaluateResult result) {
        return Tools.isFetch(request.tool()) && result.decision() == Decision.ALLOW
                && !fetcher.isAllowed(request.string(ToolRequest.ARG_URL));
    }

    private static CallStatus initialStatus(Decision decision) {
        return switch (decision) {
            case ALLOW -> CallStatus.EXECUTING;
            case DENY -> CallStatus.DENIED;
            case NEEDS_APPROVAL -> CallStatus.PENDING_APPROVAL;
        };
    }

    /**
     * SPEC 7: wait up to {@code kikomo.approval.wait-timeout} (25s) for a human decision. The approver executes the
     * call; this only observes the outcome. On timeout the agent gets {@code pending_approval} + approval_id
     * and can poll with check_approval.
     */
    private ToolEnvelope awaitDecision(CallRecord call) {
        Instant deadline = clock.instant().plus(approvalConfig.waitTimeout());
        CallRecord current = call;
        while (!current.status().isFinal() && clock.instant().isBefore(deadline) && sleepPollInterval()) {
            current = calls.findById(call.id()).orElse(current);
            if (current.status() == CallStatus.PENDING_APPROVAL && current.approvalId() != null) {
                approvals.findById(current.approvalId()).ifPresent(approvalService::expireIfOverdue);
            }
        }
        return executor.envelopeFor(calls.findById(call.id()).orElse(current));
    }

    /** @return false if the thread was interrupted (stop waiting) */
    private boolean sleepPollInterval() {
        try {
            Thread.sleep(approvalConfig.pollInterval());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
