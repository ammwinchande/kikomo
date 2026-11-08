package co.tz.kikomo.config;

import co.tz.kikomo.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Admin auth + CSRF against the real server, so cookie attributes are checked in Tomcat's actual headers. */
class SecurityConfigTest extends IntegrationTestBase {
    private static final AdminSession SESSION = new AdminSession("0123456789abcdef0123456789abcdef", "http://localhost");
    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> post(String path, String cookie, String xsrfHeader) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(url(path)).POST(HttpRequest.BodyPublishers.noBody());
        if (cookie != null) b.header("Cookie", cookie);
        if (xsrfHeader != null) b.header("X-XSRF-TOKEN", xsrfHeader);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static Optional<String> setCookie(HttpResponse<?> r, String name) {
        return r.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith(name + "=")).findFirst();
    }

    @Test
    void forgedSessionCookieIsUnauthenticated() throws Exception {
        String suspend = "/api/agents/" + newAgent().id() + "/suspend";
        assertEquals(401, post(suspend, "KIKOMO_ADMIN=ok-anything; XSRF-TOKEN=t", "t").statusCode());
    }

    @Test
    void validSessionWithoutCsrfTokenIsForbidden() throws Exception {
        String suspend = "/api/agents/" + newAgent().id() + "/suspend";
        assertEquals(403, post(suspend, "KIKOMO_ADMIN=" + SESSION.issue(), null).statusCode());
    }

    @Test
    void validSessionWithMatchingCsrfTokenIsAccepted() throws Exception {
        String suspend = "/api/agents/" + newAgent().id() + "/suspend";
        assertEquals(200, post(suspend, "KIKOMO_ADMIN=" + SESSION.issue() + "; XSRF-TOKEN=abc123", "abc123").statusCode());
    }

    @Test
    void mismatchedCsrfTokenIsForbidden() throws Exception {
        String suspend = "/api/agents/" + newAgent().id() + "/suspend";
        assertEquals(403, post(suspend, "KIKOMO_ADMIN=" + SESSION.issue() + "; XSRF-TOKEN=abc123", "other").statusCode());
    }

    @Test
    void loginIssuesHardenedSignedCookie() throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(url("/api/auth/login"))
                .header("Content-Type", "application/json").header("Cookie", "XSRF-TOKEN=t").header("X-XSRF-TOKEN", "t")
                .POST(HttpRequest.BodyPublishers.ofString("{\"password\":\"test-admin-pw\"}")).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, r.statusCode(), r.body());
        String cookie = setCookie(r, AdminSession.COOKIE_NAME).orElseThrow();
        assertTrue(cookie.contains("HttpOnly") && cookie.contains("SameSite=Strict"), cookie);
        String value = cookie.substring(cookie.indexOf('=') + 1, cookie.indexOf(';'));
        assertTrue(SESSION.verify(value), "cookie must be signed with SESSION_SECRET");
    }

    @Test
    void everyResponseCarriesStrictXsrfCookie() throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(url("/api/health")).GET().build(), HttpResponse.BodyHandlers.ofString());
        String cookie = setCookie(r, "XSRF-TOKEN").orElseThrow(() -> new AssertionError(r.headers().allValues("Set-Cookie")));
        assertTrue(cookie.contains("SameSite=Strict"), cookie);
        assertFalse(cookie.contains("HttpOnly"), "SPA must be able to read the XSRF cookie");
    }

    @Test
    void mcpDoesNotRequireCsrf() throws Exception {
        HttpResponse<String> r = mcpRaw(newAgent().token(), "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}");
        assertEquals(200, r.statusCode());
        assertTrue(List.of(r.body()).getFirst().contains("send_payment"));
    }
}
