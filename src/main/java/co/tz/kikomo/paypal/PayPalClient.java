package co.tz.kikomo.paypal;

import java.util.Map;

/**
 * PayPal REST adapter (SPEC section 9). Money-moving calls take an {@code idempotencyKey} (the Kikomo call id)
 * that is sent as {@code PayPal-Request-Id}, so a retried request cannot create a second payout.
 * Implementations throw {@link PayPalException} on any non-2xx response or transport failure.
 */
public interface PayPalClient {
    Map<String, Object> sendPayment(String idempotencyKey, String payeeEmail, long amountMinor, String currency, String note);

    Map<String, Object> createOrder(String idempotencyKey, long amountMinor, String currency, String payeeEmail);

    Map<String, Object> getOrder(String orderId);

    Map<String, Object> createInvoice(String idempotencyKey, String payeeEmail, long amountMinor, String currency);

    Map<String, Object> createRefund(String idempotencyKey, String captureId, long amountMinor, String currency);

    Map<String, Object> listTransactions();

    boolean isMock();
}
