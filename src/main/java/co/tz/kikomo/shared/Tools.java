package co.tz.kikomo.shared;
import java.util.Set;
public class Tools {
  public static final Set<String> READ_ONLY=Set.of("get_order","list_transactions","check_approval");
  public static final Set<String> ALL=Set.of("send_payment","create_order","get_order","create_invoice","create_refund","list_transactions","check_approval","kikomo_fetch_url");
  public static boolean isReadOnly(String t){ return READ_ONLY.contains(t); }
  public static boolean isRefund(String t){ return "create_refund".equals(t); }
}
