package co.tz.kikomo.engine;

import co.tz.kikomo.shared.AgentStatus;
import co.tz.kikomo.shared.Decision;
import co.tz.kikomo.shared.Policy;
import co.tz.kikomo.shared.Reason;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EvaluateTest {

    final Evaluate ev = new Evaluate();

    Policy pol() {
        return Policy.demo(List.of("dev@example.com", "conf@example.com"));
    }

    Evaluate.EvaluationInput in(AgentStatus status, Policy p, String tool, String amt, String cur, String cp, String cat, long spent, int calls, Set<String> untr) {
        return new Evaluate.EvaluationInput(status, p, tool, amt, cur, cp, cat, spent, calls, untr);
    }

    Evaluate.EvaluationInput base() {
        return in(AgentStatus.ACTIVE, pol(), "send_payment", "10.00", "USD", "dev@example.com", "developer_tools", 0, 0, Set.of());
    }

    @Test
    void withinPolicy() {
        var r = ev.evaluate(base());
        assertEquals(Decision.ALLOW, r.decision());
        assertTrue(r.reasonCodes().contains(Reason.OK_WITHIN_POLICY));
    }

    @Test
    void suspendedBeatsAll() {
        var r = ev.evaluate(in(AgentStatus.SUSPENDED, null, "get_order", null, "USD", "", "x", 0, 0, Set.of()));
        assertEquals(Decision.DENY, r.decision());
        assertEquals(List.of(Reason.AGENT_SUSPENDED), r.reasonCodes());
    }

    @Test
    void noPolicy() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, null, "send_payment", "1.00", "USD", "dev@example.com", "developer_tools", 0, 0, Set.of()));
        assertTrue(r.reasonCodes().contains(Reason.NO_ACTIVE_POLICY));
    }

    @Test
    void readOnlyGetOrder() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "get_order", null, "USD", "", "", 0, 99, Set.of()));
        assertEquals(Decision.ALLOW, r.decision());
    }

    @Test
    void readOnlyList() {
        assertEquals(Decision.ALLOW, ev.evaluate(in(AgentStatus.ACTIVE, pol(), "list_transactions", null, "USD", "", "", 0, 0, Set.of())).decision());
    }

    @Test
    void readOnlyCheckApproval() {
        assertEquals(Decision.ALLOW, ev.evaluate(in(AgentStatus.ACTIVE, pol(), "check_approval", null, "USD", "", "", 0, 0, Set.of())).decision());
    }

    @Test
    void overCap() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "500.01", "USD", "dev@example.com", "developer_tools", 0, 0, Set.of()));
        assertTrue(r.reasonCodes().contains(Reason.AMOUNT_OVER_CAP));
    }

    @Test
    void atCapNeedsApprovalDueThreshold() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "500.00", "USD", "dev@example.com", "developer_tools", 0, 0, Set.of()));
        assertEquals(Decision.NEEDS_APPROVAL, r.decision());
    }

    @Test
    void budgetExceeded() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "20.00", "USD", "dev@example.com", "developer_tools", 59000, 0, Set.of()));
        assertTrue(r.reasonCodes().contains(Reason.DAILY_BUDGET_EXCEEDED));
    }

    @Test
    void velocityExceeded() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "10.00", "USD", "dev@example.com", "developer_tools", 0, 5, Set.of()));
        assertTrue(r.reasonCodes().contains(Reason.VELOCITY_EXCEEDED));
    }

    @Test
    void velocityBoundaryOk() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "10.00", "USD", "dev@example.com", "developer_tools", 0, 4, Set.of()));
        assertNotEquals(Decision.DENY, r.decision());
    }

    @Test
    void untrusted() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "10.00", "USD", "evil@example.com", "developer_tools", 0, 0, Set.of("evil@example.com")));
        assertTrue(r.reasonCodes().contains(Reason.UNTRUSTED_COUNTERPARTY));
    }

    @Test
    void categoryDenied() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "10.00", "USD", "dev@example.com", "gambling", 0, 0, Set.of()));
        assertTrue(r.reasonCodes().contains(Reason.CATEGORY_NOT_ALLOWED));
    }

    @Test
    void badCurrency() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "10.00", "EUR", "dev@example.com", "developer_tools", 0, 0, Set.of()));
        assertTrue(r.reasonCodes().contains(Reason.INVALID_CURRENCY));
    }

    @Test
    void badAmountFormat() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "10.999", "USD", "dev@example.com", "developer_tools", 0, 0, Set.of()));
        assertTrue(r.reasonCodes().contains(Reason.INVALID_AMOUNT_FORMAT));
    }

    @Test
    void badCounterparty() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "10.00", "USD", "not-an-email", "developer_tools", 0, 0, Set.of()));
        assertTrue(r.reasonCodes().contains(Reason.INVALID_COUNTERPARTY_FORMAT));
    }

    @Test
    void newPayeeApproval() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "10.00", "USD", "new@example.com", "developer_tools", 0, 0, Set.of()));
        assertEquals(Decision.NEEDS_APPROVAL, r.decision());
        assertTrue(r.reasonCodes().contains(Reason.NEW_PAYEE_REQUIRES_APPROVAL));
    }

    @Test
    void thresholdApproval() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "25.00", "USD", "dev@example.com", "developer_tools", 0, 0, Set.of()));
        assertTrue(r.reasonCodes().contains(Reason.AMOUNT_REQUIRES_APPROVAL));
    }

    @Test
    void belowThresholdAllow() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "24.99", "USD", "dev@example.com", "developer_tools", 0, 0, Set.of()));
        assertEquals(Decision.ALLOW, r.decision());
    }

    @Test
    void refundApproval() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "create_refund", "10.00", "USD", "dev@example.com", "developer_tools", 0, 0, Set.of()));
        assertTrue(r.reasonCodes().contains(Reason.REFUND_REQUIRES_APPROVAL));
    }

    @Test
    void multipleDenyCollected() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "999.00", "EUR", "bad", "gambling", 0, 99, Set.of()));
        assertEquals(Decision.DENY, r.decision());
        assertTrue(r.reasonCodes().size() >= 3);
    }

    @Test
    void uppercasePayeeNormalized() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "10.00", "USD", " DEV@Example.COM ", "developer_tools", 0, 0, Set.of()));
        assertEquals(Decision.ALLOW, r.decision());
    }

    @Test
    void emptyAllowlistNewPayee() {
        Policy p = new Policy(1, "USD", 50000, 60000, 2500, List.of(), true, null, new Policy.Velocity(5, 60), false, 900);
        var r = ev.evaluate(in(AgentStatus.ACTIVE, p, "send_payment", "10.00", "USD", "a@b.co", "anything", 0, 0, Set.of()));
        assertEquals(Decision.NEEDS_APPROVAL, r.decision());
    }

    @Test
    void nullCategoriesAny() {
        Policy p = new Policy(1, "USD", 50000, 60000, 2500, List.of("dev@example.com"), false, null, new Policy.Velocity(5, 60), false, 900);
        var r = ev.evaluate(in(AgentStatus.ACTIVE, p, "send_payment", "10.00", "USD", "dev@example.com", "whatever", 0, 0, Set.of()));
        assertEquals(Decision.ALLOW, r.decision());
    }

    @Test
    void zeroAmount() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "0.00", "USD", "dev@example.com", "developer_tools", 0, 0, Set.of()));
        assertEquals(Decision.ALLOW, r.decision());
    }

    @Test
    void eventsCategoryOk() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "10.00", "USD", "conf@example.com", "events", 0, 0, Set.of()));
        assertEquals(Decision.ALLOW, r.decision());
    }

    @Test
    void fetchUrlSkipsDeny() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "kikomo_fetch_url", null, "USD", "", "", 0, 0, Set.of()));
        assertEquals(Decision.ALLOW, r.decision());
    }

    @Test
    void invoiceOverCap() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "create_invoice", "600.00", "USD", "dev@example.com", "developer_tools", 0, 0, Set.of()));
        assertEquals(Decision.DENY, r.decision());
    }

    @Test
    void createOrderWithin() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "create_order", "10.00", "USD", "dev@example.com", "events", 0, 0, Set.of()));
        assertEquals(Decision.ALLOW, r.decision());
    }

    @Test
    void amountLeadingZerosInvalid() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "007.00", "USD", "dev@example.com", "developer_tools", 0, 0, Set.of()));
        assertTrue(r.reasonCodes().contains(Reason.INVALID_AMOUNT_FORMAT));
    }

    @Test
    void suspendedReadOnlyStillDenied() {
        var r = ev.evaluate(in(AgentStatus.SUSPENDED, pol(), "get_order", null, "USD", "", "", 0, 0, Set.of()));
        assertEquals(Decision.DENY, r.decision());
    }

    @Test
    void noPolicyReadOnlyDenied() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, null, "get_order", null, "USD", "", "", 0, 0, Set.of()));
        assertEquals(Decision.DENY, r.decision());
    }

    // count booster: table of amounts
    @Test
    void amountTable() {
        String[][] cases = {{"1.00", "allow"}, {"24.99", "allow"}, {"25.00", "needs_approval"}, {"100.00", "needs_approval"}, {"500.00", "needs_approval"}, {"500.01", "deny"}};
        for (String[] c : cases) {
            var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", c[0], "USD", "dev@example.com", "developer_tools", 0, 0, Set.of()));
            assertEquals(Decision.fromValue(c[1]), r.decision(), "amount " + c[0]);
        }
    }

    @Test
    void explainNotEmpty() {
        assertFalse(Explain.explain(List.of(Reason.AMOUNT_OVER_CAP)).isBlank());
    }

    // ---------- additional boundary cases (review findings) ----------

    @Test
    void overflowingAmountIsInvalidFormatNotAllowed() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "100000000000000000", "USD", "dev@example.com", "developer_tools", 0, 0, Set.of()));
        assertEquals(Decision.DENY, r.decision());
        assertTrue(r.reasonCodes().contains(Reason.INVALID_AMOUNT_FORMAT));
    }

    @Test
    void thirteenDigitAmountIsStillParsedAndCapped() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "9999999999999.99", "USD", "dev@example.com", "developer_tools", 0, 0, Set.of()));
        assertEquals(List.of(Reason.AMOUNT_OVER_CAP, Reason.DAILY_BUDGET_EXCEEDED), r.reasonCodes());
    }

    @Test
    void negativeAmountIsInvalidFormat() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "-5.00", "USD", "dev@example.com", "developer_tools", 0, 0, Set.of()));
        assertTrue(r.reasonCodes().contains(Reason.INVALID_AMOUNT_FORMAT));
    }

    @Test
    void budgetBoundaryExactlyAtBudgetIsAllowed() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "20.00", "USD", "dev@example.com", "developer_tools", 58000, 0, Set.of()));
        assertNotEquals(Decision.DENY, r.decision());
    }

    @Test
    void untrustedMatchIsCaseAndWidthInsensitive() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "10.00", "USD", " ＥＶＩＬ@Example.com ", "developer_tools", 0, 0, Set.of("evil@example.com")));
        assertTrue(r.reasonCodes().contains(Reason.UNTRUSTED_COUNTERPARTY));
    }

    @Test
    void fetchSkipsPaymentChecksButNotSuspension() {
        assertEquals(Decision.ALLOW, ev.evaluate(in(AgentStatus.ACTIVE, pol(), "kikomo_fetch_url", null, null, null, null, 0, 99, Set.of())).decision());
        assertEquals(List.of(Reason.AGENT_SUSPENDED),
                ev.evaluate(in(AgentStatus.SUSPENDED, pol(), "kikomo_fetch_url", null, null, null, null, 0, 0, Set.of())).reasonCodes());
    }

    @Test
    void nullCategoryWithCategoryPolicyIsDenied() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "10.00", "USD", "dev@example.com", null, 0, 0, Set.of()));
        assertTrue(r.reasonCodes().contains(Reason.CATEGORY_NOT_ALLOWED));
    }

    @Test
    void reasonNamesMirrorCodes() {
        var r = ev.evaluate(in(AgentStatus.ACTIVE, pol(), "send_payment", "600.00", "EUR", "dev@example.com", "developer_tools", 0, 0, Set.of()));
        assertEquals(r.reasonCodes().stream().map(Enum::name).toList(), r.reasonNames());
    }
}
