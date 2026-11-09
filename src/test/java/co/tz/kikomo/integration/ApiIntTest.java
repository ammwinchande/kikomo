package co.tz.kikomo.integration;

import co.tz.kikomo.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Admin REST API: JSON shapes the SPA depends on, auth, and error status codes.
 */
class ApiIntTest extends IntegrationTestBase {

    private static Set<String> keys(JsonNode n) {
        List<String> k = new ArrayList<>(n.propertyNames());
        return Set.copyOf(k);
    }

    @Test
    void healthIsPublic() throws Exception {
        JsonNode h = new Admin().getJson("/api/health");
        assertEquals("ok", h.path("status").asString());
        assertEquals("mock", h.path("paypal_mode").asString());
        assertTrue(h.path("mock").asBoolean());
    }

    @Test
    void adminEndpointsRequireLogin() throws Exception {
        int status = new Admin().get("/api/calls").statusCode();
        assertTrue(status == 401 || status == 403, "got " + status);
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        Admin anon = new Admin();
        anon.get("/api/health");
        assertEquals(401, anon.post("/api/auth/login", "{\"password\":\"nope\"}").statusCode());
    }

    @Test
    void listShapesMatchWhatTheSpaUses() throws Exception {
        TestAgent agent = newAgent();
        callTool(agent, "send_payment", payment(ALLOWED_PAYEE, "3.00", "developer_tools"));
        Admin admin = new Admin().login();

        JsonNode agents = admin.getJson("/api/agents");
        assertTrue(agents.isArray() && !agents.isEmpty());
        assertEquals(Set.of("id", "name", "status", "spent_today_minor", "daily_budget_minor", "policy_version", "created_at"), keys(agents.get(0)));
        JsonNode mine = null;
        for (JsonNode a : agents) if (agent.id().toString().equals(a.path("id").asString())) mine = a;
        assertNotNull(mine);
        assertEquals(300, mine.path("spent_today_minor").asLong());
        assertEquals(60000, mine.path("daily_budget_minor").asLong());
        assertEquals(1, mine.path("policy_version").asInt());

        JsonNode calls = admin.getJson("/api/calls?agentId=" + agent.id());
        assertEquals(1, calls.size());
        JsonNode call = calls.get(0);
        assertEquals(Set.of("id", "agent_id", "tool", "decision", "status", "counterparty", "amount_minor", "currency", "category",
                "reason_codes", "explanation", "args", "result", "approval_id", "created_at"), keys(call));
        assertEquals("send_payment", call.path("tool").asString());
        assertEquals(300, call.path("amount_minor").asLong());
        assertEquals("USD", call.path("currency").asString());
        assertEquals("developer_tools", call.path("category").asString());
        assertTrue(call.path("reason_codes").isArray());
        assertEquals("OK_WITHIN_POLICY", call.path("reason_codes").get(0).asString());
        assertEquals("Within policy limits.", call.path("explanation").asString());
        assertEquals("3.00", call.path("args").path("amount").asString(), "args is the stored JSON document");
        assertTrue(call.path("result").isObject(), "executed calls carry the PayPal result");
        assertTrue(call.path("approval_id").isNull());
        assertFalse(call.path("created_at").asString().isBlank());

        JsonNode single = admin.getJson("/api/calls/" + call.path("id").asString());
        assertEquals(call.path("id").asString(), single.path("id").asString());

        JsonNode policy = admin.getJson("/api/policies/" + agent.id());
        assertEquals(50000, policy.path("perTxCapMinor").asLong());
        assertEquals(100, policy.path("velocity").path("maxCalls").asInt());
    }

    @Test
    void approvalShape() throws Exception {
        TestAgent agent = newAgent();
        UUID callId = UUID.randomUUID();
        jdbc.update("INSERT INTO calls(id,agent_id,tool,args,decision,status) VALUES (?,?,'send_payment','{}'::jsonb,'needs_approval','pending_approval')", callId, agent.id());
        jdbc.update("INSERT INTO approvals(call_id,agent_id,expires_at) VALUES (?,?,now() + interval '15 minutes')", callId, agent.id());
        JsonNode approvals = new Admin().login().getJson("/api/approvals?status=pending");
        JsonNode mine = null;
        for (JsonNode a : approvals) if (agent.id().toString().equals(a.path("agent_id").asString())) mine = a;
        assertNotNull(mine);
        assertEquals(Set.of("id", "call_id", "agent_id", "status", "decided_by", "decided_at", "expires_at", "created_at", "call"), keys(mine));
        assertEquals(callId.toString(), mine.path("call_id").asString());
        assertTrue(mine.path("decided_at").isNull());
        assertEquals("send_payment", mine.path("call").path("tool").asString(), "the guarded call is embedded");
        assertEquals("pending_approval", mine.path("call").path("status").asString());
    }

    @Test
    void compileReturnsDraftWithoutActivating() throws Exception {
        HttpResponse<String> r = new Admin().login().post("/api/policies/compile", "{\"text\":\"Allow dev tools up to $500\",\"allowlist\":[\"a@b.co\"]}");
        assertEquals(200, r.statusCode());
        JsonNode body = JSON.readTree(r.body());
        assertEquals("Allow dev tools up to $500", body.path("source_text").asString());
        assertEquals("a@b.co", body.path("draft").path("payeeAllowlist").get(0).asString());
    }

    @Test
    void activatingValidPolicyWorks() throws Exception {
        TestAgent agent = newAgent(null);
        HttpResponse<String> r = new Admin().login().post("/api/policies/" + agent.id() + "/activate", policyJson(5));
        assertEquals(200, r.statusCode(), r.body());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM policies WHERE agent_id=? AND status='active'", Integer.class, agent.id()));
    }

    // ---------- proper error status codes (approved behaviour change) ----------

    @Test
    void invalidPolicyIsBadRequest() throws Exception {
        TestAgent agent = newAgent();
        String invalid = policyJson(5).replace("\"perTxCapMinor\":50000", "\"perTxCapMinor\":0");
        HttpResponse<String> r = new Admin().login().post("/api/policies/" + agent.id() + "/activate", invalid);
        assertEquals(400, r.statusCode(), r.body());
        // the previous policy must still be active
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM policies WHERE agent_id=? AND status='active'", Integer.class, agent.id()));
    }

    @Test
    void unknownCallIsNotFound() throws Exception {
        assertEquals(404, new Admin().login().get("/api/calls/" + UUID.randomUUID()).statusCode());
    }

    @Test
    void unknownAgentPolicyIsNotFound() throws Exception {
        assertEquals(404, new Admin().login().get("/api/policies/" + UUID.randomUUID()).statusCode());
    }

    @Test
    void approvingUnknownApprovalIsNotFound() throws Exception {
        assertEquals(404, new Admin().login().post("/api/approvals/" + UUID.randomUUID() + "/approve", null).statusCode());
    }

    @Test
    void decidingTwiceIsConflict() throws Exception {
        TestAgent agent = newAgent();
        UUID callId = UUID.randomUUID();
        jdbc.update("INSERT INTO calls(id,agent_id,tool,args,decision,status) VALUES (?,?,'send_payment','{}'::jsonb,'needs_approval','pending_approval')", callId, agent.id());
        UUID approvalId = jdbc.queryForObject("INSERT INTO approvals(call_id,agent_id,expires_at) VALUES (?,?,now() + interval '15 minutes') RETURNING id", UUID.class, callId, agent.id());
        Admin admin = new Admin().login();
        assertEquals(200, admin.post("/api/approvals/" + approvalId + "/deny", null).statusCode());
        assertEquals(409, admin.post("/api/approvals/" + approvalId + "/deny", null).statusCode());
    }

    @Test
    void expiredApprovalCannotBeApproved() throws Exception {
        TestAgent agent = newAgent();
        UUID callId = UUID.randomUUID();
        jdbc.update("INSERT INTO calls(id,agent_id,tool,args,decision,status) VALUES (?,?,'send_payment','{}'::jsonb,'needs_approval','pending_approval')", callId, agent.id());
        UUID approvalId = jdbc.queryForObject("INSERT INTO approvals(call_id,agent_id,expires_at) VALUES (?,?,now() - interval '1 minute') RETURNING id", UUID.class, callId, agent.id());
        assertEquals(409, new Admin().login().post("/api/approvals/" + approvalId + "/approve", null).statusCode());
        assertNotEquals("approved", jdbc.queryForObject("SELECT status FROM approvals WHERE id=?", String.class, approvalId));
    }
    // ---------- endpoints added for the admin portal redesign ----------

    @Test
    void sessionProbeTellsTheSpaWhetherItIsLoggedIn() throws Exception {
        Admin anon = new Admin();
        int status = anon.get("/api/auth/session").statusCode();
        assertTrue(status == 401 || status == 403, "got " + status);
        assertEquals(200, new Admin().login().get("/api/auth/session").statusCode());
    }

    @Test
    void statsCountTodaysDecisions() throws Exception {
        Admin admin = new Admin().login();
        JsonNode before = admin.getJson("/api/stats");
        assertEquals(Set.of("pending_approvals", "allowed_today", "denied_today", "needs_approval_today", "audit_valid"), keys(before));

        TestAgent agent = newAgent();
        callTool(agent, "send_payment", payment(ALLOWED_PAYEE, "3.00", "developer_tools"));            // allow
        callTool(agent, "send_payment", payment(ALLOWED_PAYEE, "3.00", "not_a_category"));             // deny
        UUID callId = UUID.randomUUID();
        jdbc.update("INSERT INTO calls(id,agent_id,tool,args,decision,status) VALUES (?,?,'send_payment','{}'::jsonb,'needs_approval','pending_approval')", callId, agent.id());
        jdbc.update("INSERT INTO approvals(call_id,agent_id,expires_at) VALUES (?,?,now() + interval '15 minutes')", callId, agent.id());

        JsonNode after = admin.getJson("/api/stats");
        assertEquals(before.path("allowed_today").asLong() + 1, after.path("allowed_today").asLong());
        assertEquals(before.path("denied_today").asLong() + 1, after.path("denied_today").asLong());
        assertEquals(before.path("needs_approval_today").asLong() + 1, after.path("needs_approval_today").asLong());
        assertEquals(before.path("pending_approvals").asLong() + 1, after.path("pending_approvals").asLong());
        assertTrue(after.path("audit_valid").asBoolean());
    }

    @Test
    void auditEntriesAreListedOldestFirstWithLinkedHashes() throws Exception {
        TestAgent agent = newAgent();
        callTool(agent, "send_payment", payment(ALLOWED_PAYEE, "3.00", "developer_tools"));
        JsonNode entries = new Admin().login().getJson("/api/audit/entries?limit=1000");
        assertTrue(entries.isArray() && entries.size() >= 2, entries.toString());
        assertEquals(Set.of("seq", "ts", "agent_id", "event_type", "payload", "prev_hash", "hash"), keys(entries.get(0)));
        assertEquals("0".repeat(64), entries.get(0).path("prev_hash").asString(), "the first entry follows the genesis hash");
        boolean sawEvaluation = false;
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                assertEquals(entries.get(i - 1).path("seq").asLong() + 1, entries.get(i).path("seq").asLong());
                assertEquals(entries.get(i - 1).path("hash").asString(), entries.get(i).path("prev_hash").asString());
            }
            if ("call_evaluated".equals(entries.get(i).path("event_type").asString())
                    && agent.id().toString().equals(entries.get(i).path("agent_id").asString())) {
                sawEvaluation = true;
                assertTrue(entries.get(i).path("payload").isObject(), "payload is the stored JSON document: " + entries.get(i));
            }
        }
        assertTrue(sawEvaluation, "no call_evaluated entry for agent " + agent.id() + " in " + entries);
        assertEquals(2, new Admin().login().getJson("/api/audit/entries?limit=2").size());
    }

    @Test
    void spaRoutesServeTheShellButUnknownPathsDoNot() throws Exception {
        Admin anon = new Admin();
        for (String page : List.of("/login", "/approvals", "/activity", "/audit", "/policy", "/demo")) {
            HttpResponse<String> r = anon.get(page);
            assertEquals(200, r.statusCode(), page);
            assertTrue(r.headers().firstValue("content-type").orElse("").startsWith("text/html"), page);
        }
        assertEquals(404, anon.get("/no-such-page").statusCode());
        int api = anon.get("/api/no-such-endpoint").statusCode();
        assertTrue(api == 401 || api == 403, "API stays protected, got " + api);
    }
}
