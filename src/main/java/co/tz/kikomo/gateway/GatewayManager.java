package co.tz.kikomo.gateway;
import co.tz.kikomo.audit.AuditService; import co.tz.kikomo.db.Repositories; import co.tz.kikomo.engine.*; import co.tz.kikomo.paypal.PayPalClient; import co.tz.kikomo.shared.*;
import com.fasterxml.jackson.databind.ObjectMapper; import org.springframework.stereotype.Service;
import java.util.*; import java.util.concurrent.TimeUnit;
@Service
public class GatewayManager {
  private final Repositories repo; private final PayPalClient paypal; private final AuditService audit; private final Evaluate evaluate=new Evaluate(); private final ObjectMapper mapper=new ObjectMapper();
  public GatewayManager(Repositories repo, PayPalClient paypal, AuditService audit){ this.repo=repo; this.paypal=paypal; this.audit=audit; }
  public record ToolRequest(String tool, Map<String,Object> args) {}
  public Map<String,Object> execute(UUID agentId, String tool, Map<String,Object> args){
    var agentOpt=repo.findAgentById(agentId);
    String agentStatus=agentOpt.map(a->(String)a.get("status")).orElse("suspended");
    Policy policy=repo.activePolicy(agentId);
    String amountRaw=args.get("amount")==null?null:String.valueOf(args.get("amount"));
    String currency=args.get("currency")==null?"USD":String.valueOf(args.get("currency"));
    String counterparty=args.get("payee_email")!=null?String.valueOf(args.get("payee_email")):(args.get("counterparty")!=null?String.valueOf(args.get("counterparty")):"");
    String category=args.get("category")==null?null:String.valueOf(args.get("category"));
    // kikomo_fetch_url handled by caller provenance; still evaluate read path via engine (not read-only, but no deny checks)
    long spent=policy==null?0:repo.spentToday(agentId);
    int windowCalls=(policy!=null&&policy.velocity()!=null)?repo.callsInWindow(agentId, policy.velocity().windowSeconds()):0;
    var input=new Evaluate.EvaluationInput(agentStatus, policy, tool, amountRaw, currency, counterparty, category, spent, windowCalls, repo.untrusted(agentId));
    var result=evaluate.evaluate(input);
    List<String> codes=result.reasonCodes().stream().map(Enum::name).toList();
    String status = switch(result.decision()){ case "allow"->"executed"; case "deny"->"denied"; default->"pending_approval"; };
    Long amountMinor=null; try{ if(amountRaw!=null&&Money.isValidFormat(amountRaw)) amountMinor=Money.parseMinor(amountRaw); }catch(Exception ignored){}
    UUID callId;
    try{ callId=repo.insertCall(agentId, tool, mapper.writeValueAsString(args), amountMinor, currency, Normalize.payee(counterparty), category, result.decision(), codes, status); }
    catch(Exception e){ throw new RuntimeException(e); }
    safeAudit(agentId,"call_evaluated", Map.of("call_id",callId.toString(),"tool",tool,"decision",result.decision(),"reason_codes",codes));
    Map<String,Object> envelope=new LinkedHashMap<>();
    envelope.put("call_id", callId.toString()); envelope.put("decision", result.decision()); envelope.put("reason_codes", codes); envelope.put("explanation", Explain.explain(result.reasonCodes()));
    if("deny".equals(result.decision())){ envelope.put("status","denied"); return envelope; }
    if("needs_approval".equals(result.decision())){
      int ttl=policy!=null?policy.approvalTtlSeconds():900;
      UUID approvalId=repo.insertApproval(callId, agentId, ttl);
      repo.updateCall(callId,"pending_approval",null,approvalId);
      envelope.put("status","pending_approval"); envelope.put("approval_id",approvalId.toString());
      // poll up to 25s
      long deadline=System.currentTimeMillis()+25000;
      while(System.currentTimeMillis()<deadline){
        try{ TimeUnit.MILLISECONDS.sleep(500);}catch(InterruptedException e){ Thread.currentThread().interrupt(); break; }
        var appr=repo.getApproval(approvalId); if(appr==null) break; String st=(String)appr.get("status");
        if("approved".equals(st)){ return executePayPal(agentId, tool, args, callId, envelope, amountMinor, currency, Normalize.payee(counterparty)); }
        if("denied".equals(st)||"expired".equals(st)){ repo.updateCall(callId, "denied".equals(st)?"denied":"expired", null, null); envelope.put("status", "denied".equals(st)?"denied":"expired"); safeAudit(agentId,"approval_"+st, Map.of("call_id",callId.toString())); return envelope; }
      }
      return envelope;
    }
    return executePayPal(agentId, tool, args, callId, envelope, amountMinor, currency, Normalize.payee(counterparty));
  }
  private Map<String,Object> executePayPal(UUID agentId,String tool,Map<String,Object> args,UUID callId,Map<String,Object> envelope,Long amountMinor,String currency,String counterparty){
    try{
      Map<String,Object> pp=switch(tool){
        case "send_payment" -> paypal.sendPayment(counterparty, amountMinor==null?0:amountMinor, currency, args.get("note")==null?"Kikomo payment":String.valueOf(args.get("note")));
        case "create_order" -> paypal.createOrder(amountMinor==null?0:amountMinor, currency, counterparty);
        case "get_order" -> paypal.getOrder(String.valueOf(args.get("order_id")));
        case "create_invoice" -> paypal.createInvoice(counterparty, amountMinor==null?0:amountMinor, currency);
        case "create_refund" -> paypal.createRefund(String.valueOf(args.get("capture_id")), amountMinor==null?0:amountMinor, currency);
        case "list_transactions" -> paypal.listTransactions();
        case "check_approval" -> checkApproval(args);
        case "kikomo_fetch_url" -> Map.of("note","fetch handled via provenance service");
        default -> Map.of("error","unknown tool");
      };
      repo.updateCall(callId,"executed", mapper.writeValueAsString(pp), null);
      if(amountMinor!=null && ("send_payment".equals(tool)||"create_order".equals(tool)||"create_invoice".equals(tool))) repo.addSpend(agentId, callId, amountMinor, currency, counterparty);
      envelope.put("status","executed"); envelope.put("paypal", pp);
      safeAudit(agentId,"call_executed", Map.of("call_id",callId.toString(),"tool",tool));
      return envelope;
    }catch(Exception e){ repo.updateCall(callId,"failed", null, null); envelope.put("status","failed"); envelope.put("error", e.getMessage()); safeAudit(agentId,"call_failed", Map.of("call_id",callId.toString(),"error",String.valueOf(e.getMessage()))); return envelope; }
  }
  public Map<String,Object> checkApproval(Map<String,Object> args){
    Object id=args.get("approval_id"); if(id==null) id=args.get("call_id");
    if(id==null) return Map.of("status","unknown");
    try{ var a=repo.getApproval(UUID.fromString(String.valueOf(id))); if(a!=null) return Map.of("approval_id",String.valueOf(id),"status",a.get("status"));
      var c=repo.getCall(UUID.fromString(String.valueOf(id))); if(c!=null) return Map.of("call_id",String.valueOf(id),"status",c.get("status"),"decision",c.get("decision")); }catch(Exception ignored){}
    return Map.of("status","not_found");
  }
  private void safeAudit(UUID agentId,String type,Object payload){ try{ audit.append(agentId,type,payload);}catch(Exception ignored){} }
  public PayPalClient paypal(){ return paypal; }
}
