package co.tz.kikomo.paypal;
import com.fasterxml.jackson.databind.*; import org.springframework.beans.factory.annotation.Value; import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; import org.springframework.stereotype.Component;
import java.net.*; import java.net.http.*; import java.util.*;
@Component @ConditionalOnProperty(name="kikomo.paypal-mode", havingValue="live")
public class LivePayPalClient implements PayPalClient {
  private final String base, clientId, clientSecret; private final HttpClient http=HttpClient.newHttpClient(); private final ObjectMapper mapper=new ObjectMapper();
  public LivePayPalClient(@Value("${kikomo.paypal-base-url:https://api-m.sandbox.paypal.com}") String base,
    @Value("${PAYPAL_CLIENT_ID:}") String id, @Value("${PAYPAL_CLIENT_SECRET:}") String secret){ this.base=base; this.clientId=id; this.clientSecret=secret; }
  public boolean isMock(){ return false; }
  private String token(){ try{
    String basic=Base64.getEncoder().encodeToString((clientId+":"+clientSecret).getBytes());
    var req=HttpRequest.newBuilder(URI.create(base+"/v1/oauth2/token")).header("Authorization","Basic "+basic).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials")).build();
    var res=http.send(req, HttpResponse.BodyHandlers.ofString()); return mapper.readTree(res.body()).path("access_token").asText();
  }catch(Exception e){ throw new RuntimeException("PayPal OAuth failed: "+e.getMessage(), e); } }
  private Map<String,Object> call(String method,String path,Object body){ try{
    var b=HttpRequest.newBuilder(URI.create(base+path)).header("Authorization","Bearer "+token()).header("Content-Type","application/json");
    if(body!=null) b.method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))); else b.method(method, HttpRequest.BodyPublishers.noBody());
    var res=http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    JsonNode n=mapper.readTree(res.body().isBlank()?"{}":res.body()); Map<String,Object> out=mapper.convertValue(n, Map.class);
    out.put("_http_status", res.statusCode()); return out;
  }catch(Exception e){ throw new RuntimeException(e); } }
  public Map<String,Object> sendPayment(String e,long a,String c,String n){
    var item=Map.of("recipient_type","EMAIL","receiver",e,"amount",Map.of("value",String.format("%.2f",a/100.0),"currency",c),"note",n==null?"Kikomo payment":n,"sender_item_id","item-"+UUID.randomUUID());
    return call("POST","/v1/payments/payouts", Map.of("sender_batch_header",Map.of("sender_batch_id","batch-"+UUID.randomUUID(),"email_subject","Kikomo payment"),"items",List.of(item))); }
  public Map<String,Object> createOrder(long a,String c,String e){ return call("POST","/v2/checkout/orders", Map.of("intent","CAPTURE","purchase_units",List.of(Map.of("amount",Map.of("value",String.format("%.2f",a/100.0),"currency_code",c),"payee",Map.of("email_address",e))))); }
  public Map<String,Object> getOrder(String id){ return call("GET","/v2/checkout/orders/"+id,null); }
  public Map<String,Object> createInvoice(String e,long a,String c){ return call("POST","/v2/invoicing/invoices", Map.of("detail",Map.of("currency_code",c),"primary_recipients",List.of(Map.of("billing_info",Map.of("email_address",e))),"items",List.of(Map.of("name","Kikomo invoice","quantity","1","unit_amount",Map.of("currency_code",c,"value",String.format("%.2f",a/100.0)))))); }
  public Map<String,Object> createRefund(String cap,long a,String c){ return call("POST","/v2/payments/captures/"+cap+"/refund", Map.of("amount",Map.of("value",String.format("%.2f",a/100.0),"currency_code",c))); }
  public Map<String,Object> listTransactions(){ return call("GET","/v1/reporting/transactions?start_date=2020-01-01T00:00:00-0700&end_date=2030-01-01T00:00:00-0700",null); }
}
