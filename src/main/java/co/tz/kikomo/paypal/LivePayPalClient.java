package co.tz.kikomo.paypal;

import co.tz.kikomo.config.HttpClientConfig;
import co.tz.kikomo.shared.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sandbox REST client using the JDK {@link HttpClient}.
 */
public class LivePayPalClient implements PayPalClient {

    private static final Logger log = LoggerFactory.getLogger(LivePayPalClient.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };
    private static final DateTimeFormatter REPORTING_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    /**
     * PayPal's transaction search accepts at most a 31-day window.
     */
    private static final int TRANSACTION_WINDOW_DAYS = 30;
    /**
     * Refresh the OAuth token this long before PayPal says it expires.
     */
    private static final long TOKEN_EXPIRY_MARGIN_SECONDS = 60;
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_REQUEST_ID = "PayPal-Request-Id";
    private static final String JSON_CONTENT = "application/json";
    private static final String KEY_AMOUNT = "amount";
    private static final String KEY_VALUE = "value";
    private static final String KEY_CURRENCY_CODE = "currency_code";
    private static final String KEY_EMAIL_ADDRESS = "email_address";
    private static final String DEFAULT_NOTE = "Kikomo payment";

    private final String baseUrl;
    private final String clientId;
    private final String clientSecret;
    private final HttpClient http;
    private final JsonMapper json;
    private final Clock clock;
    private final Object tokenLock = new Object();
    private String accessToken;
    private Instant accessTokenExpiresAt = Instant.EPOCH;

    public LivePayPalClient(String baseUrl, String clientId, String clientSecret, HttpClient http, JsonMapper json, Clock clock) {
        this.baseUrl = baseUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.http = http;
        this.json = json;
        this.clock = clock;
    }

    private static Map<String, Object> money(long amountMinor, String currency) {
        return Map.of(KEY_VALUE, Money.formatMinor(amountMinor), KEY_CURRENCY_CODE, currency);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String abbreviate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 500 ? body : body.substring(0, 500) + "...";
    }

    @Override
    public boolean isMock() {
        return false;
    }

    @Override
    public Map<String, Object> sendPayment(String idempotencyKey, String payeeEmail, long amountMinor, String currency, String note) {
        Map<String, Object> item = Map.of(
                "recipient_type", "EMAIL",
                "receiver", payeeEmail,
                KEY_AMOUNT, Map.of(KEY_VALUE, Money.formatMinor(amountMinor), "currency", currency),
                "note", note == null ? DEFAULT_NOTE : note,
                "sender_item_id", "item-" + idempotencyKey);
        Map<String, Object> body = Map.of(
                "sender_batch_header", Map.of("sender_batch_id", "kikomo-" + idempotencyKey, "email_subject", DEFAULT_NOTE),
                "items", List.of(item));
        return send("POST", "/v1/payments/payouts", body, idempotencyKey);
    }

    @Override
    public Map<String, Object> createOrder(String idempotencyKey, long amountMinor, String currency, String payeeEmail) {
        Map<String, Object> unit = Map.of(
                KEY_AMOUNT, money(amountMinor, currency),
                "payee", Map.of(KEY_EMAIL_ADDRESS, payeeEmail));
        return send("POST", "/v2/checkout/orders", Map.of("intent", "CAPTURE", "purchase_units", List.of(unit)), idempotencyKey);
    }

    @Override
    public Map<String, Object> getOrder(String orderId) {
        return send("GET", "/v2/checkout/orders/" + orderId, null, null);
    }

    @Override
    public Map<String, Object> createInvoice(String idempotencyKey, String payeeEmail, long amountMinor, String currency) {
        Map<String, Object> body = Map.of(
                "detail", Map.of(KEY_CURRENCY_CODE, currency),
                "primary_recipients", List.of(Map.of("billing_info", Map.of(KEY_EMAIL_ADDRESS, payeeEmail))),
                "items", List.of(Map.of("name", "Kikomo invoice", "quantity", "1", "unit_amount", money(amountMinor, currency))));
        return send("POST", "/v2/invoicing/invoices", body, idempotencyKey);
    }

    @Override
    public Map<String, Object> createRefund(String idempotencyKey, String captureId, long amountMinor, String currency) {
        return send("POST", "/v2/payments/captures/" + captureId + "/refund", Map.of(KEY_AMOUNT, money(amountMinor, currency)), idempotencyKey);
    }

    @Override
    public Map<String, Object> listTransactions() {
        OffsetDateTime end = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        OffsetDateTime start = end.minusDays(TRANSACTION_WINDOW_DAYS);
        String query = "?start_date=" + encode(REPORTING_TIME.format(start)) + "&end_date=" + encode(REPORTING_TIME.format(end));
        return send("GET", "/v1/reporting/transactions" + query, null, null);
    }

    /**
     * Fetches (or reuses) an OAuth token; used by the spike to verify credentials.
     */
    public String accessToken() {
        synchronized (tokenLock) {
            if (accessToken != null && clock.instant().isBefore(accessTokenExpiresAt)) {
                return accessToken;
            }
            String basic = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/v1/oauth2/token"))
                    .timeout(HttpClientConfig.REQUEST_TIMEOUT)
                    .header(HEADER_AUTHORIZATION, "Basic " + basic)
                    .header(HEADER_CONTENT_TYPE, "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                    .build();
            HttpResponse<String> response = execute(request, "OAuth");
            JsonNode body = json.readTree(response.body());
            String token = body.path("access_token").asString("");
            if (token.isBlank()) {
                throw new PayPalException("PayPal OAuth returned no access_token", response.statusCode());
            }
            accessToken = token;
            accessTokenExpiresAt = clock.instant().plusSeconds(Math.max(0, body.path("expires_in").asLong(0) - TOKEN_EXPIRY_MARGIN_SECONDS));
            return token;
        }
    }

    private Map<String, Object> send(String method, String path, Object body, String idempotencyKey) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(HttpClientConfig.REQUEST_TIMEOUT)
                .header(HEADER_AUTHORIZATION, "Bearer " + accessToken())
                .header(HEADER_CONTENT_TYPE, JSON_CONTENT)
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if (idempotencyKey != null) {
            builder.header(HEADER_REQUEST_ID, idempotencyKey);
        }
        HttpResponse<String> response = execute(builder.build(), method + " " + path);
        Map<String, Object> out = new LinkedHashMap<>(parseBody(response));
        out.put("_http_status", response.statusCode());
        return out;
    }

    private HttpResponse<String> execute(HttpRequest request, String operation) {
        HttpResponse<String> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PayPalException("PayPal " + operation + " was interrupted", e);
        } catch (IOException e) {
            throw new PayPalException("PayPal " + operation + " failed: " + e.getMessage(), e);
        }
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            String debugId = response.headers().firstValue("paypal-debug-id").orElse("n/a");
            log.warn("PayPal {} returned HTTP {} (debug_id={}): abbreviated body={}", operation, status, debugId, abbreviate(response.body()));
            throw new PayPalException("PayPal " + operation + " returned HTTP " + status + " (debug_id=" + debugId + ")", status);
        }
        return response;
    }

    private Map<String, Object> parseBody(HttpResponse<String> response) {
        String body = response.body();
        if (body == null || body.isBlank()) return Map.of();
        try {
            return json.readValue(body, MAP);
        } catch (JacksonException e) {
            throw new PayPalException("PayPal returned a non-JSON body (HTTP " + response.statusCode() + ")", e);
        }
    }
}
