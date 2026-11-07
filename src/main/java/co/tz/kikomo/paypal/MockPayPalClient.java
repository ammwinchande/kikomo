package co.tz.kikomo.paypal;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.stereotype.Component; import java.util.*;
@Component @ConditionalOnProperty(name="kikomo.paypal-mode", havingValue="mock", matchIfMissing=true)
public class MockPayPalClient implements PayPalClient {
  public boolean isMock(){ return true; }
  public Map<String,Object> sendPayment(String e,long a,String c,String n){ return Map.of("id","MOCK-PAYOUT-"+UUID.randomUUID(),"status","SUCCESS","payee",e,"amount_minor",a,"currency",c); }
  public Map<String,Object> createOrder(long a,String c,String e){ return Map.of("id","MOCK-ORDER-"+UUID.randomUUID(),"status","CREATED","amount_minor",a,"currency",c); }
  public Map<String,Object> getOrder(String id){ return Map.of("id",id,"status","APPROVED","mock",true); }
  public Map<String,Object> createInvoice(String e,long a,String c){ return Map.of("id","MOCK-INV-"+UUID.randomUUID(),"status","SENT","payee",e,"amount_minor",a); }
  public Map<String,Object> createRefund(String cap,long a,String c){ return Map.of("id","MOCK-REF-"+UUID.randomUUID(),"status","COMPLETED","capture_id",cap,"amount_minor",a); }
  public Map<String,Object> listTransactions(){ return Map.of("transactions",List.of(),"mock",true); }
}
