package co.tz.kikomo.shared;
import jakarta.validation.constraints.*;
import java.util.List;
public record Policy(
  int version, String currency,
  @Min(1) @Max(100000) long perTxCapMinor,
  long dailyBudgetMinor,
  @Min(1) long approvalThresholdMinor,
  @Size(max=50) List<String> payeeAllowlist,
  boolean newPayeeRequiresApproval,
  List<String> allowedCategories,
  Velocity velocity, boolean refundsRequireApproval,
  @Min(60) @Max(86400) int approvalTtlSeconds) {
  public record Velocity(@Min(1) int maxCalls, @Min(1) int windowSeconds) {}
  public String effectiveCurrency(){ return currency==null||currency.isBlank()?"USD":currency; }
  public static Policy demo(List<String> allow){
    return new Policy(1,"USD",50000,60000,2500,allow,true,List.of("developer_tools","events"),new Velocity(5,60),true,900);
  }
}
