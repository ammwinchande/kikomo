package co.tz.kikomo.engine;

import co.tz.kikomo.shared.AgentStatus;
import co.tz.kikomo.shared.Decision;
import co.tz.kikomo.shared.Money;
import co.tz.kikomo.shared.Policy;
import co.tz.kikomo.shared.Reason;
import co.tz.kikomo.shared.Tools;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Deterministic policy engine (SPEC 6.3). Pure function: no I/O, no clock, no model. The order of checks and
 * of the returned reason codes is part of the contract.
 */
public class Evaluate {

    public EvaluateResult evaluate(EvaluationInput in) {
        if (in.agentStatus() == AgentStatus.SUSPENDED) {
            return deny(List.of(Reason.AGENT_SUSPENDED));
        }
        if (in.policy() == null) {
            return deny(List.of(Reason.NO_ACTIVE_POLICY));
        }
        if (Tools.isReadOnly(in.tool())) {
            return new EvaluateResult(Decision.ALLOW, List.of(Reason.OK_READ_ONLY));
        }
        if (Tools.isFetch(in.tool())) {
            return allowWithinPolicy();
        }

        Policy policy = in.policy();
        String counterparty = Normalize.payee(in.counterpartyRaw());
        Long amountMinor = parseAmount(in.amountRaw());

        List<Reason> denyReasons = denyReasons(in, policy, counterparty, amountMinor);
        if (!denyReasons.isEmpty()) {
            return deny(denyReasons);
        }
        List<Reason> approvalReasons = approvalReasons(in, policy, counterparty, amountMinor);
        if (!approvalReasons.isEmpty()) {
            return new EvaluateResult(Decision.NEEDS_APPROVAL, List.copyOf(approvalReasons));
        }
        return allowWithinPolicy();
    }

    private List<Reason> denyReasons(EvaluationInput in, Policy policy, String counterparty, Long amountMinor) {
        List<Reason> reasons = new ArrayList<>();
        if (isInvalidCounterparty(counterparty)) reasons.add(Reason.INVALID_COUNTERPARTY_FORMAT);
        if (isInvalidAmountFormat(in.amountRaw())) reasons.add(Reason.INVALID_AMOUNT_FORMAT);
        if (isCurrencyMismatch(policy, in.currency())) reasons.add(Reason.INVALID_CURRENCY);
        if (amountMinor != null) {
            if (amountMinor > policy.perTxCapMinor()) reasons.add(Reason.AMOUNT_OVER_CAP);
            if (in.spentTodayMinor() + amountMinor > policy.dailyBudgetMinor()) reasons.add(Reason.DAILY_BUDGET_EXCEEDED);
        }
        if (isVelocityExceeded(policy, in.callsInWindow())) reasons.add(Reason.VELOCITY_EXCEEDED);
        if (isUntrusted(in.untrustedNormalized(), counterparty)) reasons.add(Reason.UNTRUSTED_COUNTERPARTY);
        if (isCategoryNotAllowed(policy, in.category())) reasons.add(Reason.CATEGORY_NOT_ALLOWED);
        return reasons;
    }

    private List<Reason> approvalReasons(EvaluationInput in, Policy policy, String counterparty, Long amountMinor) {
        List<Reason> reasons = new ArrayList<>();
        if (policy.newPayeeRequiresApproval() && !normalizedAllowlist(policy).contains(counterparty)) {
            reasons.add(Reason.NEW_PAYEE_REQUIRES_APPROVAL);
        }
        if (amountMinor != null && amountMinor >= policy.approvalThresholdMinor()) reasons.add(Reason.AMOUNT_REQUIRES_APPROVAL);
        if (Tools.isRefund(in.tool()) && policy.refundsRequireApproval()) reasons.add(Reason.REFUND_REQUIRES_APPROVAL);
        return reasons;
    }

    /** @return minor units, or null when the amount is absent or malformed */
    private static Long parseAmount(String raw) {
        return Money.isValidFormat(raw) ? Money.parseMinor(raw) : null;
    }

    private static boolean isInvalidCounterparty(String counterparty) {
        return counterparty.isEmpty() || !Normalize.isValidEmail(counterparty);
    }

    private static boolean isInvalidAmountFormat(String raw) {
        return raw != null && !Money.isValidFormat(raw);
    }

    private static boolean isCurrencyMismatch(Policy policy, String currency) {
        String requested = currency == null ? Money.DEFAULT_CURRENCY : currency;
        return !policy.effectiveCurrency().equals(requested);
    }

    private static boolean isVelocityExceeded(Policy policy, int callsInWindow) {
        return policy.velocity() != null && callsInWindow >= policy.velocity().maxCalls();
    }

    private static boolean isUntrusted(Set<String> untrusted, String counterparty) {
        return untrusted != null && untrusted.contains(counterparty);
    }

    private static boolean isCategoryNotAllowed(Policy policy, String category) {
        List<String> allowed = policy.allowedCategories();
        if (allowed == null || allowed.isEmpty()) {
            return false;
        }
        return category == null || !allowed.contains(category);
    }

    private static List<String> normalizedAllowlist(Policy policy) {
        return policy.payeeAllowlist() == null
                ? List.of()
                : policy.payeeAllowlist().stream().map(Normalize::payee).toList();
    }

    private static EvaluateResult allowWithinPolicy() {
        return new EvaluateResult(Decision.ALLOW, List.of(Reason.OK_WITHIN_POLICY));
    }

    private static EvaluateResult deny(List<Reason> reasons) {
        return new EvaluateResult(Decision.DENY, List.copyOf(reasons));
    }

    public record EvaluationInput(AgentStatus agentStatus, Policy policy, String tool, String amountRaw,
                                  String currency, String counterpartyRaw, String category, long spentTodayMinor,
                                  int callsInWindow, Set<String> untrustedNormalized) {
    }

    public record EvaluateResult(Decision decision, List<Reason> reasonCodes) {
        public List<String> reasonNames() {
            return reasonCodes.stream().map(Enum::name).toList();
        }
    }
}
