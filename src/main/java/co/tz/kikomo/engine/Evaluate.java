package co.tz.kikomo.engine;
import co.tz.kikomo.shared.*; import java.util.*;
public class Evaluate {
  public record EvaluationInput(String agentStatus, Policy policy, String tool, String amountRaw,
    String currency, String counterpartyRaw, String category, long spentTodayMinor,
    int callsInWindow, Set<String> untrustedNormalized) {}
  public record EvaluateResult(String decision, List<Reason> reasonCodes) {}
  public EvaluateResult evaluate(EvaluationInput in){
    if("suspended".equals(in.agentStatus())) return deny(List.of(Reason.AGENT_SUSPENDED));
    if(in.policy()==null) return deny(List.of(Reason.NO_ACTIVE_POLICY));
    if(Tools.isReadOnly(in.tool())) return new EvaluateResult("allow", List.of(Reason.OK_READ_ONLY));
    Policy p=in.policy(); List<Reason> deny=new ArrayList<>();
    String cp=Normalize.payee(in.counterpartyRaw());
    boolean isFetch="kikomo_fetch_url".equals(in.tool());
    if(!isFetch){
      if(cp.isEmpty()||!Normalize.isValidEmail(cp)) deny.add(Reason.INVALID_COUNTERPARTY_FORMAT);
      if(in.amountRaw()!=null && !Money.isValidFormat(in.amountRaw())) deny.add(Reason.INVALID_AMOUNT_FORMAT);
      String cur=in.currency()==null?"USD":in.currency();
      if(!p.effectiveCurrency().equals(cur)) deny.add(Reason.INVALID_CURRENCY);
      long amt=0; boolean amtOk=false;
      if(in.amountRaw()!=null && Money.isValidFormat(in.amountRaw())){ try{ amt=Money.parseMinor(in.amountRaw()); amtOk=true;}catch(Exception e){} }
      if(amtOk && amt>p.perTxCapMinor()) deny.add(Reason.AMOUNT_OVER_CAP);
      if(amtOk && in.spentTodayMinor()+amt>p.dailyBudgetMinor()) deny.add(Reason.DAILY_BUDGET_EXCEEDED);
      if(p.velocity()!=null && in.callsInWindow()>=p.velocity().maxCalls()) deny.add(Reason.VELOCITY_EXCEEDED);
      if(in.untrustedNormalized()!=null && in.untrustedNormalized().contains(cp)) deny.add(Reason.UNTRUSTED_COUNTERPARTY);
      if(p.allowedCategories()!=null && !p.allowedCategories().isEmpty()){
        if(in.category()==null||!p.allowedCategories().contains(in.category())) deny.add(Reason.CATEGORY_NOT_ALLOWED);
      }
    }
    if(!deny.isEmpty()) return deny(deny);
    List<Reason> appr=new ArrayList<>();
    if(!isFetch){
      List<String> allow=p.payeeAllowlist()==null?List.of():p.payeeAllowlist().stream().map(Normalize::payee).toList();
      if(p.newPayeeRequiresApproval() && !allow.contains(cp)) appr.add(Reason.NEW_PAYEE_REQUIRES_APPROVAL);
      if(in.amountRaw()!=null && Money.isValidFormat(in.amountRaw()) && Money.parseMinor(in.amountRaw())>=p.approvalThresholdMinor()) appr.add(Reason.AMOUNT_REQUIRES_APPROVAL);
      if(Tools.isRefund(in.tool()) && p.refundsRequireApproval()) appr.add(Reason.REFUND_REQUIRES_APPROVAL);
    }
    if(!appr.isEmpty()) return new EvaluateResult("needs_approval", List.copyOf(appr));
    return new EvaluateResult("allow", List.of(Reason.OK_WITHIN_POLICY));
  }
  private EvaluateResult deny(List<Reason> r){ return new EvaluateResult("deny", List.copyOf(r)); }
}
