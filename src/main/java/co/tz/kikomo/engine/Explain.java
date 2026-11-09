package co.tz.kikomo.engine;

import co.tz.kikomo.shared.Reason;

import java.util.List;

public final class Explain {

    private Explain() {
        /* This utility class should not be instantiated */
    }

    public static String explain(List<Reason> codes) {
        if (codes == null || codes.isEmpty()) {
            return "No reason codes.";
        }

        List<String> parts = codes.stream().map(r -> switch (r) {
            case OK_READ_ONLY -> "Read-only tool allowed.";
            case OK_WITHIN_POLICY -> "Within policy limits.";
            case AGENT_SUSPENDED -> "Agent is suspended by kill switch.";
            case NO_ACTIVE_POLICY -> "No active policy for agent.";
            case INVALID_AMOUNT_FORMAT -> "Amount format is invalid.";
            case INVALID_CURRENCY -> "Currency does not match policy currency.";
            case AMOUNT_OVER_CAP -> "Amount exceeds per-transaction cap.";
            case DAILY_BUDGET_EXCEEDED -> "Daily budget would be exceeded.";
            case VELOCITY_EXCEEDED -> "Too many calls in velocity window.";
            case UNTRUSTED_COUNTERPARTY -> "Counterparty came from untrusted fetched content.";
            case CATEGORY_NOT_ALLOWED -> "Category is not allowed by policy.";
            case INVALID_COUNTERPARTY_FORMAT -> "Counterparty is not a valid email.";
            case SSRF_BLOCKED -> "URL host is not in FETCH_ALLOWED_HOSTS.";
            case NEW_PAYEE_REQUIRES_APPROVAL -> "New payee requires human approval.";
            case AMOUNT_REQUIRES_APPROVAL -> "Amount is at or above approval threshold.";
            case REFUND_REQUIRES_APPROVAL -> "Refunds require human approval.";
        }).toList();

        return String.join(" ", parts);
    }
}
