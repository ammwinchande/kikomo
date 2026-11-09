package co.tz.kikomo.integration;

import co.tz.kikomo.gateway.ApprovalService;
import co.tz.kikomo.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Approval timeout/resume, expiry and concurrency, with a short approval wait so tests stay fast.
 */
@TestPropertySource(properties = {"kikomo.approval.wait-timeout=1s", "kikomo.approval.poll-interval=100ms"})
class ApprovalLifecycleIntTest extends IntegrationTestBase {

    @Autowired
    ApprovalService approvalService;
    
    @Test
    void approvalAfterAgentStoppedWaitingStillExecutesExactlyOnce() throws Exception {
        TestAgent agent = newAgent();
        JsonNode pending = callTool(agent, "send_payment", payment(ALLOWED_PAYEE, "100.00", "developer_tools"));
        assertEquals("pending_approval", pending.path("status").asString(), pending.toString());
        String approvalId = pending.path("approval_id").asString();
        assertFalse(approvalId.isBlank());

        Admin admin = new Admin().login();
        assertEquals(200, admin.post("/api/approvals/" + approvalId + "/approve", null).statusCode());
        assertEquals(409, admin.post("/api/approvals/" + approvalId + "/approve", null).statusCode(), "second approval must conflict");

        JsonNode check = callTool(agent, "check_approval", "{\"approval_id\":\"" + approvalId + "\"}");
        assertEquals("approved", check.path("paypal").path("status").asString(), check.toString());
        assertEquals("executed", check.path("paypal").path("call_status").asString(), check.toString());
        assertTrue(check.path("paypal").path("result").path("id").asString().startsWith("MOCK-PAYOUT-"), check.toString());

        assertEquals(10000L, jdbc.queryForObject("SELECT COALESCE(SUM(amount_minor),0) FROM spend_ledger WHERE agent_id=?", Long.class, agent.id()));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM spend_ledger WHERE agent_id=?", Integer.class, agent.id()));
    }

    @Test
    void deniedAfterTimeoutMarksCallDenied() throws Exception {
        TestAgent agent = newAgent();
        JsonNode pending = callTool(agent, "send_payment", payment(OTHER_PAYEE, "100.00", "events"));
        String approvalId = pending.path("approval_id").asString();
        assertEquals(200, new Admin().login().post("/api/approvals/" + approvalId + "/deny", null).statusCode());
        assertEquals("denied", jdbc.queryForObject("SELECT status FROM calls WHERE agent_id=?", String.class, agent.id()));
    }

    @Test
    void overdueApprovalsAreSweptToExpired() throws Exception {
        TestAgent agent = newAgent();
        JsonNode pending = callTool(agent, "send_payment", payment(ALLOWED_PAYEE, "100.00", "developer_tools"));
        UUID approvalId = UUID.fromString(pending.path("approval_id").asString());
        jdbc.update("UPDATE approvals SET expires_at = now() - interval '1 second' WHERE id=?", approvalId);

        approvalService.expireOverdue();

        assertEquals("expired", jdbc.queryForObject("SELECT status FROM approvals WHERE id=?", String.class, approvalId));
        assertEquals("expired", jdbc.queryForObject("SELECT status FROM calls WHERE agent_id=?", String.class, agent.id()));
        Integer audited = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE agent_id=? AND event_type='approval_expired'", Integer.class, agent.id());
        assertEquals(1, audited);
    }

    @Test
    void concurrentPaymentsCannotOverspendTheDailyBudget() throws Exception {
        // cap 500, budget 600, no approvals below 500: four parallel 200.00 payments fit individually,
        // but only three fit the budget together.
        TestAgent agent = newAgent(policyJson(100).replace("\"approvalThresholdMinor\":2500", "\"approvalThresholdMinor\":50000"));
        List<CompletableFuture<JsonNode>> futures = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    return callTool(agent, "send_payment", payment(ALLOWED_PAYEE, "200.00", "developer_tools"));
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }));
        }
        int executed = 0;
        int budgetDenied = 0;
        for (CompletableFuture<JsonNode> f : futures) {
            JsonNode r = f.get(30, TimeUnit.SECONDS);
            if ("executed".equals(r.path("status").asString())) executed++;
            if (r.path("reason_codes").toString().contains("DAILY_BUDGET_EXCEEDED")) budgetDenied++;
        }
        assertEquals(3, executed);
        assertEquals(1, budgetDenied);
        assertEquals(60000L, jdbc.queryForObject("SELECT COALESCE(SUM(amount_minor),0) FROM spend_ledger WHERE agent_id=?", Long.class, agent.id()));
    }
}
