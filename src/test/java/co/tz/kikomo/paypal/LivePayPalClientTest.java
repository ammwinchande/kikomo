package co.tz.kikomo.paypal;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** LivePayPalClient against a local stub of the PayPal REST API. */
class LivePayPalClientTest {
    private HttpServer server;
    private final AtomicInteger tokenCalls = new AtomicInteger();
    private final List<String> payoutBodies = new CopyOnWriteArrayList<>();
    private final List<String> requestIds = new CopyOnWriteArrayList<>();
    private volatile int payoutStatus = 201;
    private LivePayPalClient client;
    private HttpClient http;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/oauth2/token", ex -> {
            tokenCalls.incrementAndGet();
            respond(ex, 200, "{\"access_token\":\"tok\",\"expires_in\":3600}");
        });
        server.createContext("/v1/payments/payouts", ex -> {
            payoutBodies.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            requestIds.add(ex.getRequestHeaders().getFirst("PayPal-Request-Id"));
            respond(ex, payoutStatus, payoutStatus == 201 ? "{\"batch_header\":{\"payout_batch_id\":\"B1\"}}" : "{\"name\":\"INSUFFICIENT_FUNDS\"}");
        });
        server.start();
        http = HttpClient.newHttpClient();
        client = new LivePayPalClient("http://127.0.0.1:" + server.getAddress().getPort(), "id", "secret", http, JsonMapper.builder().build(), Clock.systemUTC());
    }

    @AfterEach
    void stop() {
        server.stop(0);
        http.close();
    }

    private static void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    @Test
    void successfulPayoutSendsIdempotencyKeyAndLocaleIndependentAmount() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            var result = client.sendPayment("call-1", "dev@example.com", 1250, "USD", "hi");
            assertEquals(201, result.get("_http_status"));
            assertTrue(payoutBodies.getFirst().contains("\"value\":\"12.50\""), payoutBodies.getFirst());
            assertEquals("call-1", requestIds.getFirst());
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void non2xxIsAnErrorNotASuccess() {
        payoutStatus = 422;
        PayPalException e = assertThrows(PayPalException.class, () -> client.sendPayment("call-2", "dev@example.com", 100, "USD", null));
        assertEquals(422, e.httpStatus());
    }

    @Test
    void oauthTokenIsCached() {
        client.sendPayment("a", "dev@example.com", 100, "USD", null);
        client.sendPayment("b", "dev@example.com", 100, "USD", null);
        assertEquals(1, tokenCalls.get());
    }
}
