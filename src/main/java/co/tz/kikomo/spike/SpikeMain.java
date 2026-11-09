package co.tz.kikomo.spike;

import co.tz.kikomo.paypal.LivePayPalClient;
import co.tz.kikomo.paypal.MockPayPalClient;
import co.tz.kikomo.paypal.PayPalException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 0 spike ({@code ./gradlew runSpike}, SPEC section 9). In live mode it hits the PayPal sandbox: OAuth,
 * then creates and reads back a USD 1.00 checkout order (no money moves until a buyer approves it).
 * Exits non-zero on failure.
 */
public final class SpikeMain {
    private static final Logger log = LoggerFactory.getLogger(SpikeMain.class);
    private static final String DEFAULT_BASE_URL = "https://api-m.sandbox.paypal.com";

    private SpikeMain() {
    }

    public static void main(String[] args) {
        String mode = System.getenv().getOrDefault("PAYPAL_MODE", "mock");
        log.info("Kikomo spike: PAYPAL_MODE={}", mode);
        if (!"live".equalsIgnoreCase(mode)) {
            log.info("mock send_payment -> {}", new MockPayPalClient().sendPayment(UUID.randomUUID().toString(), "dev@example.com", 1000, "USD", "spike"));
            log.info("SPIKE PASS (mock)");
            return;
        }
        String clientId = System.getenv("PAYPAL_CLIENT_ID");
        String secret = System.getenv("PAYPAL_CLIENT_SECRET");
        if (clientId == null || clientId.isBlank() || secret == null || secret.isBlank()) {
            log.error("SPIKE FAIL: PAYPAL_MODE=live but PAYPAL_CLIENT_ID/PAYPAL_CLIENT_SECRET are missing (see NOTES.md, Needs the owner)");
            System.exit(1);
        }
        String baseUrl = System.getenv().getOrDefault("PAYPAL_BASE_URL", DEFAULT_BASE_URL);
        try (HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()) {
            LivePayPalClient client = new LivePayPalClient(baseUrl, clientId, secret, http, JsonMapper.builder().build(), Clock.systemUTC());
            client.accessToken();
            log.info("OAuth OK against {}", baseUrl);
            Map<String, Object> order = client.createOrder(UUID.randomUUID().toString(), 100, "USD", "spike@example.com");
            String orderId = String.valueOf(order.get("id"));
            log.info("create_order OK: id={} status={}", orderId, order.get("status"));
            log.info("get_order OK: status={}", client.getOrder(orderId).get("status"));
            log.info("SPIKE PASS (live sandbox)");
        } catch (PayPalException e) {
            log.error("SPIKE FAIL: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
