package co.tz.kikomo.support;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Full application against a real Postgres (Testcontainers). Tests talk to the app only over HTTP
 * (MCP + admin API) and set up state with plain SQL, so they stay valid across internal refactors.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "ADMIN_PASSWORD=test-admin-pw",
        "SESSION_SECRET=0123456789abcdef0123456789abcdef",
        "DEMO_AGENT_TOKEN=seed-token-0123456789abcdef0123456789",
        "PAYPAL_MODE=mock"
})
public abstract class IntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    protected static final JsonMapper JSON = JsonMapper.builder().build();
    protected static final String ALLOWED_PAYEE = "devtools@example.com";
    protected static final String OTHER_PAYEE = "conf@example.com";

    @LocalServerPort
    protected int port;
    @Autowired
    protected JdbcTemplate jdbc;

    private final HttpClient mcpHttp = HttpClient.newHttpClient();

    protected URI url(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    // ---------- agents ----------

    public record TestAgent(UUID id, String token) {
    }

    /** Demo-like policy, but with a generous velocity limit so tests are not throttled. */
    protected static String policyJson(int maxCalls) {
        return """
                {"version":1,"currency":"USD","perTxCapMinor":50000,"dailyBudgetMinor":60000,
                 "approvalThresholdMinor":2500,"payeeAllowlist":["%s","%s"],"newPayeeRequiresApproval":true,
                 "allowedCategories":["developer_tools","events"],"velocity":{"maxCalls":%d,"windowSeconds":60},
                 "refundsRequireApproval":true,"approvalTtlSeconds":900}""".formatted(ALLOWED_PAYEE, OTHER_PAYEE, maxCalls);
    }

    protected TestAgent newAgent() {
        return newAgent(policyJson(100));
    }

    protected TestAgent newAgent(String policyJson) {
        UUID id = UUID.randomUUID();
        String token = "tok-" + UUID.randomUUID();
        jdbc.update("INSERT INTO agents(id,name,token_hash,status) VALUES (?,?,?,'active')", id, "test-" + id, sha256(token));
        if (policyJson != null) {
            jdbc.update("INSERT INTO policies(agent_id,version,doc,status,confirmed_at) VALUES (?,1,?::jsonb,'active',now())", id, policyJson);
        }
        return new TestAgent(id, token);
    }

    protected static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---------- MCP ----------

    protected HttpResponse<String> mcpRaw(String token, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(url("/mcp"))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (token != null) b.header("Authorization", "Bearer " + token);
        return mcpHttp.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** Calls an MCP tool and returns the parsed tool result (the JSON text content). */
    protected JsonNode callTool(TestAgent agent, String tool, String argumentsJson) throws Exception {
        String body = """
                {"jsonrpc":"2.0","id":"%s","method":"tools/call","params":{"name":"%s","arguments":%s}}"""
                .formatted(UUID.randomUUID(), tool, argumentsJson);
        HttpResponse<String> res = mcpRaw(agent.token(), body);
        JsonNode rpc = JSON.readTree(res.body());
        if (rpc.has("error")) return rpc;
        return JSON.readTree(rpc.path("result").path("content").path(0).path("text").asString());
    }

    protected static String payment(String payee, String amount, String category) {
        return """
                {"payee_email":"%s","amount":"%s","currency":"USD","category":"%s"}""".formatted(payee, amount, category);
    }

    // ---------- admin API ----------

    /** Logged-in admin HTTP client that handles the session + XSRF cookies like the SPA does. */
    public class Admin {
        public Admin() {
            // anonymous until login()
        }

        private final CookieManager cookies = new CookieManager();
        private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();

        public Admin login() throws Exception {
            get("/api/health"); // obtains XSRF-TOKEN cookie
            HttpResponse<String> r = post("/api/auth/login", "{\"password\":\"test-admin-pw\"}");
            if (r.statusCode() != 200) throw new IllegalStateException("login failed: " + r.statusCode());
            return this;
        }

        private String xsrf() {
            return cookies.getCookieStore().getCookies().stream()
                    .filter(c -> "XSRF-TOKEN".equals(c.getName())).map(HttpCookie::getValue).findFirst().orElse("");
        }

        public HttpResponse<String> get(String path) throws Exception {
            return http.send(HttpRequest.newBuilder(url(path)).GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        public HttpResponse<String> post(String path, String json) throws Exception {
            return http.send(HttpRequest.newBuilder(url(path))
                    .header("Content-Type", "application/json")
                    .header("X-XSRF-TOKEN", xsrf())
                    .POST(json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json))
                    .build(), HttpResponse.BodyHandlers.ofString());
        }

        public JsonNode getJson(String path) throws Exception {
            return JSON.readTree(get(path).body());
        }
    }
}
