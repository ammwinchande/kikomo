package co.tz.kikomo.integration;

import co.tz.kikomo.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end gateway behaviour over MCP against a real Postgres.
 */
class GatewayIntTest extends IntegrationTestBase {

    private static List<String> codes(JsonNode result) {
        List<String> out = new ArrayList<>();
        result.path("reason_codes").forEach(n -> out.add(n.asString()));
        return out;
    }

    @Test
    void smallPaymentToAllowlistedPayeeExecutes() throws Exception {
        TestAgent agent = newAgent();
        JsonNode r = callTool(agent, "send_payment", payment(ALLOWED_PAYEE, "10.00", "developer_tools"));

        assertEquals("executed", r.path("status").asString(), r.toString());
        assertEquals("allow", r.path("decision").asString());
        assertEquals(List.of("OK_WITHIN_POLICY"), codes(r));
        assertFalse(r.path("call_id").asString().isBlank());
        assertFalse(r.path("explanation").asString().isBlank());
        assertTrue(r.path("paypal").path("id").asString().startsWith("MOCK-PAYOUT-"), r.toString());

        Long spent = jdbc.queryForObject("SELECT COALESCE(SUM(amount_minor),0) FROM spend_ledger WHERE agent_id=?", Long.class, agent.id());
        assertEquals(1000L, spent);
        assertEquals("executed", jdbc.queryForObject("SELECT status FROM calls WHERE agent_id=?", String.class, agent.id()));
    }

    @Test
    void overCapIsDenied() throws Exception {
        JsonNode r = callTool(newAgent(), "send_payment", payment(ALLOWED_PAYEE, "600.00", "developer_tools"));
        assertEquals("denied", r.path("status").asString());
        assertEquals("deny", r.path("decision").asString());
        assertTrue(codes(r).contains("AMOUNT_OVER_CAP"), r.toString());
    }

    @Test
    void allDenyReasonsAreCollected() throws Exception {
        JsonNode r = callTool(newAgent(), "send_payment", """
                {"payee_email":"not-an-email","amount":"1.234","currency":"EUR","category":"gambling"}""");
        assertEquals("denied", r.path("status").asString());
        assertEquals(List.of("INVALID_COUNTERPARTY_FORMAT", "INVALID_AMOUNT_FORMAT", "INVALID_CURRENCY", "CATEGORY_NOT_ALLOWED"), codes(r));
    }

    @Test
    void readOnlyToolIsAllowed() throws Exception {
        JsonNode r = callTool(newAgent(), "get_order", "{\"order_id\":\"ORDER-123\"}");
        assertEquals("executed", r.path("status").asString(), r.toString());
        assertEquals(List.of("OK_READ_ONLY"), codes(r));
    }

    @Test
    void agentWithoutPolicyIsDenied() throws Exception {
        JsonNode r = callTool(newAgent(null), "send_payment", payment(ALLOWED_PAYEE, "1.00", "developer_tools"));
        assertEquals(List.of("NO_ACTIVE_POLICY"), codes(r));
    }

    @Test
    void dailyBudgetIsEnforced() throws Exception {
        TestAgent agent = newAgent(policyJson(100).replace("\"approvalThresholdMinor\":2500", "\"approvalThresholdMinor\":50000"));
        assertEquals("executed", callTool(agent, "send_payment", payment(ALLOWED_PAYEE, "400.00", "developer_tools")).path("status").asString());
        JsonNode second = callTool(agent, "send_payment", payment(ALLOWED_PAYEE, "300.00", "developer_tools"));
        assertEquals(List.of("DAILY_BUDGET_EXCEEDED"), codes(second));
    }

    @Test
    void velocityIsEnforced() throws Exception {
        TestAgent agent = newAgent(policyJson(2));
        callTool(agent, "send_payment", payment(ALLOWED_PAYEE, "1.00", "developer_tools"));
        callTool(agent, "send_payment", payment(ALLOWED_PAYEE, "1.00", "developer_tools"));
        JsonNode third = callTool(agent, "send_payment", payment(ALLOWED_PAYEE, "1.00", "developer_tools"));
        assertTrue(codes(third).contains("VELOCITY_EXCEEDED"), third.toString());
    }

    @Test
    void suspendedAgentIsDenied() throws Exception {
        TestAgent agent = newAgent();
        Admin admin = new Admin().login();
        assertEquals(200, admin.post("/api/agents/" + agent.id() + "/suspend", null).statusCode());
        JsonNode r = callTool(agent, "send_payment", payment(ALLOWED_PAYEE, "1.00", "developer_tools"));
        assertEquals(List.of("AGENT_SUSPENDED"), codes(r));
        assertEquals(200, admin.post("/api/agents/" + agent.id() + "/activate", null).statusCode());
        assertEquals("executed", callTool(agent, "send_payment", payment(ALLOWED_PAYEE, "1.00", "developer_tools")).path("status").asString());
    }

    @Test
    void injectedPayeeFromFetchedPageIsDenied() throws Exception {
        TestAgent agent = newAgent();
        JsonNode fetch = callTool(agent, "kikomo_fetch_url", "{\"url\":\"http://localhost:" + port + "/demo/malicious-page\"}");
        assertEquals("executed", fetch.path("status").asString(), fetch.toString());
        assertTrue(fetch.path("emails_found").toString().contains("pay-me@evil-payments.example"), fetch.toString());

        JsonNode pay = callTool(agent, "send_payment", payment("pay-me@evil-payments.example", "5.00", "developer_tools"));
        assertEquals("denied", pay.path("status").asString());
        assertTrue(codes(pay).contains("UNTRUSTED_COUNTERPARTY"), pay.toString());
    }

    @Test
    void fetchOfDisallowedHostIsBlocked() throws Exception {
        JsonNode r = callTool(newAgent(), "kikomo_fetch_url", "{\"url\":\"http://169.254.169.254/latest/meta-data\"}");
        assertEquals("denied", r.path("status").asString(), r.toString());
        assertEquals(List.of("SSRF_BLOCKED"), codes(r));
    }

    @Test
    void largePaymentWaitsForApprovalThenExecutes() throws Exception {
        TestAgent agent = newAgent();
        Admin admin = new Admin().login();
        CompletableFuture<JsonNode> pending = CompletableFuture.supplyAsync(() -> {
            try {
                return callTool(agent, "send_payment", payment(ALLOWED_PAYEE, "100.00", "developer_tools"));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        String approvalId = awaitPendingApproval(admin, agent);
        assertEquals(200, admin.post("/api/approvals/" + approvalId + "/approve", null).statusCode());

        JsonNode r = pending.get(30, TimeUnit.SECONDS);
        assertEquals("executed", r.path("status").asString(), r.toString());
        assertEquals("needs_approval", r.path("decision").asString());
        assertEquals(List.of("AMOUNT_REQUIRES_APPROVAL"), codes(r));
        assertEquals(approvalId, r.path("approval_id").asString());
        assertTrue(r.path("paypal").path("id").asString().startsWith("MOCK-PAYOUT-"));
    }

    @Test
    void largePaymentDeniedByAdmin() throws Exception {
        TestAgent agent = newAgent();
        Admin admin = new Admin().login();
        CompletableFuture<JsonNode> pending = CompletableFuture.supplyAsync(() -> {
            try {
                return callTool(agent, "send_payment", payment(OTHER_PAYEE, "100.00", "events"));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        String approvalId = awaitPendingApproval(admin, agent);
        assertEquals(200, admin.post("/api/approvals/" + approvalId + "/deny", null).statusCode());
        JsonNode r = pending.get(30, TimeUnit.SECONDS);
        assertEquals("denied", r.path("status").asString(), r.toString());
        assertEquals("denied", jdbc.queryForObject("SELECT status FROM calls WHERE agent_id=?", String.class, agent.id()));
    }

    // ---------- regression tests for review findings ----------

    @Test
    void auditLogRecordsEveryCallAndVerifies() throws Exception {
        TestAgent agent = newAgent();
        callTool(agent, "send_payment", payment(ALLOWED_PAYEE, "2.00", "developer_tools"));
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE agent_id=?", Integer.class, agent.id());
        assertTrue(rows != null && rows >= 2, "expected call_evaluated + call_executed audit rows, got " + rows);
        assertTrue(new Admin().login().getJson("/api/audit/verify").path("valid").asBoolean());
    }

    @Test
    void overflowingAmountIsRejectedNotAllowed() throws Exception {
        TestAgent agent = newAgent();
        JsonNode r = callTool(agent, "send_payment", payment(ALLOWED_PAYEE, "100000000000000000", "developer_tools"));
        assertEquals("denied", r.path("status").asString(), r.toString());
        assertTrue(codes(r).contains("INVALID_AMOUNT_FORMAT"), r.toString());
        Long spent = jdbc.queryForObject("SELECT COALESCE(SUM(amount_minor),0) FROM spend_ledger WHERE agent_id=?", Long.class, agent.id());
        assertEquals(0L, spent);
    }

    @Test
    void unknownToolIsRejected() throws Exception {
        TestAgent agent = newAgent();
        JsonNode r = callTool(agent, "drain_account", payment(ALLOWED_PAYEE, "1.00", "developer_tools"));
        assertTrue(r.has("error"), "expected JSON-RPC error, got " + r);
        Integer calls = jdbc.queryForObject("SELECT COUNT(*) FROM calls WHERE agent_id=? AND status='executed'", Integer.class, agent.id());
        assertEquals(0, calls);
    }

    @Test
    void refundWithPathTraversalCaptureIdIsRejected() throws Exception {
        TestAgent agent = newAgent(policyJson(100).replace("\"refundsRequireApproval\":true", "\"refundsRequireApproval\":false"));
        JsonNode r = callTool(agent, "create_refund", """
                {"capture_id":"../../v1/payments/payouts","amount":"1.00","currency":"USD","payee_email":"%s","category":"developer_tools"}"""
                .formatted(ALLOWED_PAYEE));
        assertNotEquals("executed", r.path("status").asString(), r.toString());
    }

    private String awaitPendingApproval(Admin admin, TestAgent agent) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            for (JsonNode a : admin.getJson("/api/approvals?status=pending")) {
                if (agent.id().toString().equals(a.path("agent_id").asString())) {
                    return a.path("id").asString();
                }
            }
            await().atMost(200, TimeUnit.MILLISECONDS);
        }
        throw new AssertionError("no pending approval for agent " + agent.id());
    }
}
