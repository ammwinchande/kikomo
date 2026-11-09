package co.tz.kikomo.integration;

import co.tz.kikomo.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Demo runner: the scripted agent talks to the app over real MCP as the seeded demo agent.
 */
@TestPropertySource(properties = {"kikomo.approval.wait-timeout=2s", "kikomo.approval.poll-interval=100ms"})
class DemoIntTest extends IntegrationTestBase {

    private static Set<String> keys(JsonNode n) {
        return Set.copyOf(List.copyOf(n.propertyNames()));
    }

    private static JsonNode lastStep(JsonNode run) {
        JsonNode steps = run.path("steps");
        return steps.get(steps.size() - 1);
    }

    private static List<String> labels(JsonNode run) {
        return run.path("steps").valueStream().map(s -> s.path("label").asString()).toList();
    }

    /**
     * The seeded demo policy allows 5 calls a minute and $600 a day; several scenarios in a row would trip both,
     * so lift them for tests.
     */
    @BeforeEach
    void liftDemoAgentLimits() {
        jdbc.update("""
                UPDATE policies SET doc = jsonb_set(jsonb_set(doc, '{velocity,maxCalls}', '1000'::jsonb), '{dailyBudgetMinor}', '100000000'::jsonb)
                WHERE status = 'active' AND agent_id = (SELECT id FROM agents WHERE name = 'demo-agent')""");
    }

    private JsonNode start(Admin admin, String scenario) throws Exception {
        HttpResponse<String> r = admin.post("/api/demo/runs", "{\"scenario\":\"" + scenario + "\"}");
        assertEquals(200, r.statusCode(), r.body());
        JsonNode run = JSON.readTree(r.body());
        assertEquals(Set.of("id", "scenario", "status", "started_at", "steps"), keys(run));
        assertEquals(scenario, run.path("scenario").asString());
        return run;
    }

    private JsonNode awaitStatus(Admin admin, String id, Duration timeout, String... statuses) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        JsonNode run = null;
        while (Instant.now().isBefore(deadline)) {
            run = admin.getJson("/api/demo/runs/" + id);
            for (String s : statuses) {
                if (s.equals(run.path("status").asString())) {
                    return run;
                }
            }
            await().atMost(Duration.ofMillis(200));
        }
        fail("run did not reach " + List.of(statuses) + " in time: " + run);
        return run;
    }

    @Test
    void demoEndpointsRequireLogin() throws Exception {
        int status = new Admin().post("/api/demo/runs", "{\"scenario\":\"small\"}").statusCode();
        assertTrue(status == 401 || status == 403, "got " + status);
    }

    @Test
    void unknownScenarioIsBadRequest() throws Exception {
        assertEquals(400, new Admin().login().post("/api/demo/runs", "{\"scenario\":\"huge\"}").statusCode());
    }

    @Test
    void unknownRunIsNotFound() throws Exception {
        assertEquals(404, new Admin().login().get("/api/demo/runs/" + UUID.randomUUID()).statusCode());
    }

    @Test
    void connectInfoNeverLeaksTheToken() throws Exception {
        JsonNode c = new Admin().login().getJson("/api/demo/connect");
        assertEquals("http://localhost:8080/mcp", c.path("mcp_url").asString());
        assertTrue(c.path("malicious_page_url").asString().endsWith("/demo/malicious-page"));
        assertFalse(c.toString().contains("seed-token"), c.toString());
    }

    @Test
    void smallPaymentIsAllowedAndExecuted() throws Exception {
        Admin admin = new Admin().login();
        JsonNode run = awaitStatus(admin, start(admin, "small").path("id").asString(), Duration.ofSeconds(20), "done", "failed");
        assertEquals("done", run.path("status").asString(), run.toString());
        assertEquals(List.of("send_payment", "OK_WITHIN_POLICY", "executed"), labels(run));
        JsonNode step = run.path("steps").get(1);
        assertEquals(Set.of("id", "kind", "title", "label", "detail", "call_id", "approval_id"), keys(step));
        assertEquals("allow", step.path("kind").asString());
        assertEquals("executed", jdbc.queryForObject("SELECT status FROM calls WHERE id = ?::uuid", String.class, step.path("call_id").asString()));
    }

    @Test
    void injectionIsDeniedBecauseThePayeeCameFromAPage() throws Exception {
        Admin admin = new Admin().login();
        JsonNode run = awaitStatus(admin, start(admin, "injection").path("id").asString(), Duration.ofSeconds(20), "done", "failed");
        assertEquals("done", run.path("status").asString(), run.toString());
        assertEquals(List.of("kikomo_fetch_url", "prompt injection", "UNTRUSTED_COUNTERPARTY"), labels(run));
        assertEquals("deny", lastStep(run).path("kind").asString());
    }

    @Test
    void largePaymentWaitsForTheHumanAndExecutesWhenApproved() throws Exception {
        Admin admin = new Admin().login();
        String id = start(admin, "large").path("id").asString();

        // The feed shows the pending approval (with its id) while the agent is still waiting.
        Instant deadline = Instant.now().plusSeconds(10);
        String approvalId = null;
        while (approvalId == null && Instant.now().isBefore(deadline)) {
            for (JsonNode s : admin.getJson("/api/demo/runs/" + id).path("steps")) {
                if ("pending_approval".equals(s.path("label").asString()))
                    approvalId = s.path("approval_id").asString();
            }
            await().atMost(Duration.ofMillis(100));
        }
        assertNotNull(approvalId, "approval step never appeared");

        // The 2 s agent wait elapses first; the run stays open until the human decides.
        await().atMost(Duration.ofMillis(2500));

        assertEquals("running", admin.getJson("/api/demo/runs/" + id).path("status").asString());

        assertEquals(200, admin.post("/api/approvals/" + approvalId + "/approve", null).statusCode());
        JsonNode run = awaitStatus(admin, id, Duration.ofSeconds(20), "done", "failed");
        assertEquals("done", run.path("status").asString(), run.toString());
        List<String> labels = labels(run);
        assertTrue(labels.containsAll(List.of("send_payment", "AMOUNT_REQUIRES_APPROVAL", "pending_approval", "approved", "executed")), labels.toString());
        assertEquals("executed", lastStep(run).path("label").asString());
    }

    @Test
    void runAllChainsTheThreeScenarios() throws Exception {
        Admin admin = new Admin().login();
        String id = start(admin, "all").path("id").asString();
        Instant deadline = Instant.now().plusSeconds(15);
        String approvalId = null;
        while (approvalId == null && Instant.now().isBefore(deadline)) {
            for (JsonNode s : admin.getJson("/api/demo/runs/" + id).path("steps")) {
                if ("pending_approval".equals(s.path("label").asString()))
                    approvalId = s.path("approval_id").asString();
            }
            await().atMost(Duration.ofMillis(100));
        }
        assertNotNull(approvalId);
        assertEquals(200, admin.post("/api/approvals/" + approvalId + "/deny", null).statusCode());
        JsonNode run = awaitStatus(admin, id, Duration.ofSeconds(20), "done", "failed");
        List<String> labels = labels(run);
        assertTrue(labels.indexOf("OK_WITHIN_POLICY") < labels.indexOf("UNTRUSTED_COUNTERPARTY"), labels.toString());
        assertTrue(labels.indexOf("UNTRUSTED_COUNTERPARTY") < labels.indexOf("pending_approval"), labels.toString());
        assertEquals("denied", lastStep(run).path("label").asString());
    }
}
