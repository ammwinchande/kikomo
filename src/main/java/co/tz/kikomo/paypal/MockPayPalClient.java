package co.tz.kikomo.paypal;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Offline PayPal used when {@code PAYPAL_MODE=mock}: no network, no money moves. */
public class MockPayPalClient implements PayPalClient {
    private static final String KEY_ID = "id";
    private static final String KEY_STATUS = "status";
    private static final String KEY_PAYEE = "payee";
    private static final String KEY_AMOUNT_MINOR = "amount_minor";
    private static final String KEY_CURRENCY = "currency";
    private static final String KEY_MOCK = "mock";

    @Override
    public boolean isMock() {
        return true;
    }

    @Override
    public Map<String, Object> sendPayment(String idempotencyKey, String payeeEmail, long amountMinor, String currency, String note) {
        return Map.of(KEY_ID, "MOCK-PAYOUT-" + UUID.randomUUID(), KEY_STATUS, "SUCCESS", KEY_PAYEE, payeeEmail,
                KEY_AMOUNT_MINOR, amountMinor, KEY_CURRENCY, currency);
    }

    @Override
    public Map<String, Object> createOrder(String idempotencyKey, long amountMinor, String currency, String payeeEmail) {
        return Map.of(KEY_ID, "MOCK-ORDER-" + UUID.randomUUID(), KEY_STATUS, "CREATED", KEY_AMOUNT_MINOR, amountMinor, KEY_CURRENCY, currency);
    }

    @Override
    public Map<String, Object> getOrder(String orderId) {
        return Map.of(KEY_ID, orderId, KEY_STATUS, "APPROVED", KEY_MOCK, true);
    }

    @Override
    public Map<String, Object> createInvoice(String idempotencyKey, String payeeEmail, long amountMinor, String currency) {
        return Map.of(KEY_ID, "MOCK-INV-" + UUID.randomUUID(), KEY_STATUS, "SENT", KEY_PAYEE, payeeEmail, KEY_AMOUNT_MINOR, amountMinor);
    }

    @Override
    public Map<String, Object> createRefund(String idempotencyKey, String captureId, long amountMinor, String currency) {
        return Map.of(KEY_ID, "MOCK-REF-" + UUID.randomUUID(), KEY_STATUS, "COMPLETED", "capture_id", captureId, KEY_AMOUNT_MINOR, amountMinor);
    }

    @Override
    public Map<String, Object> listTransactions() {
        return Map.of("transactions", List.of(), KEY_MOCK, true);
    }
}
