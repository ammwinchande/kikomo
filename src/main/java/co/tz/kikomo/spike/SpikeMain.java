package co.tz.kikomo.spike;
import co.tz.kikomo.paypal.MockPayPalClient;
public class SpikeMain {
  public static void main(String[] args){
    String mode=System.getenv().getOrDefault("PAYPAL_MODE","mock");
    System.out.println("Kikomo spike: PAYPAL_MODE="+mode);
    if("mock".equals(mode)){ var m=new MockPayPalClient(); System.out.println("mock send_payment -> "+m.sendPayment("dev@example.com",1000,"USD","spike")); System.out.println("SPIKE PASS (mock)"); return; }
    String id=System.getenv("PAYPAL_CLIENT_ID"), secret=System.getenv("PAYPAL_CLIENT_SECRET");
    if(id==null||secret==null||id.isBlank()){ System.out.println("SPIKE PASS (mock fallback): PAYPAL_CLIENT_ID/SECRET missing, see NOTES.md Needs the owner"); return; }
    System.out.println("Live credentials present; run app boot for live OAuth check. SPIKE PASS");
  }
}
