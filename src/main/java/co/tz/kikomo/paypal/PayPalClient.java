package co.tz.kikomo.paypal;
import java.util.Map;
public interface PayPalClient {
  Map<String,Object> sendPayment(String payeeEmail, long amountMinor, String currency, String note);
  Map<String,Object> createOrder(long amountMinor, String currency, String payeeEmail);
  Map<String,Object> getOrder(String orderId);
  Map<String,Object> createInvoice(String payeeEmail, long amountMinor, String currency);
  Map<String,Object> createRefund(String captureId, long amountMinor, String currency);
  Map<String,Object> listTransactions();
  boolean isMock();
}
