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
        assertEquals(Set.of("id", "name", "status"), keys(agents.get(0)));

        JsonNode calls = admin.getJson("/api/calls?agentId=" + agent.id());
        assertEquals(1, calls.size());
        JsonNode call = calls.get(0);
        assertEquals(Set.of("id", "agent_id", "tool", "decision", "status", "counterparty", "amount_minor", "reason_codes"), keys(call));
        assertEquals("send_payment", call.path("tool").asString());
        assertEquals(300, call.path("amount_minor").asLong());
        assertTrue(call.path("reason_codes").isArray());
        assertEquals("OK_WITHIN_POLICY", call.path("reason_codes").get(0).asString());

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
        assertEquals(Set.of("id", "call_id", "agent_id", "status", "expires_at"), keys(mine));
        assertEquals(callId.toString(), mine.path("call_id").asString());
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
}
