package co.tz.kikomo.llm;
import co.tz.kikomo.shared.Policy; import com.fasterxml.jackson.databind.*; import org.springframework.beans.factory.annotation.Value; import org.springframework.stereotype.Service;
import java.net.*; import java.net.http.*; import java.util.*;
@Service
public class CompilePolicy {
  private final String provider, apiKey, baseUrl, model; private final ObjectMapper mapper=new ObjectMapper();
  public CompilePolicy(@Value("${LLM_PROVIDER:stub}") String provider, @Value("${LLM_API_KEY:}") String apiKey,
    @Value("${LLM_BASE_URL:https://api.meta.ai/v1}") String baseUrl, @Value("${LLM_MODEL:muse-spark-1.2-contributor}") String model){ this.provider=provider; this.apiKey=apiKey; this.baseUrl=baseUrl; this.model=model; }
  public Policy compile(String text, List<String> defaultAllowlist){
    if("stub".equalsIgnoreCase(provider) || apiKey==null || apiKey.isBlank()) return stubCompile(text, defaultAllowlist);
    try{
      String prompt="Return ONLY JSON for a Kikomo Policy with fields: version,currency,perTxCapMinor,dailyBudgetMinor,approvalThresholdMinor,payeeAllowlist,newPayeeRequiresApproval,allowedCategories,velocity{maxCalls,windowSeconds},refundsRequireApproval,approvalTtlSeconds. HARD_MAX_TX_MINOR=100000. Text: "+text;
      var body=Map.of("model",model,"messages",List.of(Map.of("role","user","content",prompt)),"temperature",0);
      var req=HttpRequest.newBuilder(URI.create(baseUrl+"/chat/completions")).header("Authorization","Bearer "+apiKey).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
      var res=HttpClient.newHttpClient().send(req, HttpResponse.BodyHandlers.ofString());
      String content=mapper.readTree(res.body()).path("choices").path(0).path("message").path("content").asText();
      int s=content.indexOf('{'), e=content.lastIndexOf('}'); if(s>=0&&e>s) content=content.substring(s,e+1);
      Policy p=mapper.readValue(content, Policy.class); validate(p); return p;
    }catch(Exception e){ return stubCompile(text, defaultAllowlist); }
  }
  Policy stubCompile(String text, List<String> allow){
    String t=text==null?"":text.toLowerCase();
    long cap=50000, budget=60000, thresh=2500; int ttl=900;
    // naive extraction keeps demo deterministic offline
    return new Policy(1,"USD",cap,budget,thresh, allow==null?List.of():allow, true, List.of("developer_tools","events"), new Policy.Velocity(5,60), true, ttl);
  }
  public static void validate(Policy p){
    if(p.perTxCapMinor()<1||p.perTxCapMinor()>100000) throw new IllegalArgumentException("perTxCapMinor out of range");
    if(p.dailyBudgetMinor()<p.perTxCapMinor()) throw new IllegalArgumentException("dailyBudgetMinor must be >= perTxCapMinor");
    if(p.approvalThresholdMinor()<1||p.approvalThresholdMinor()>p.perTxCapMinor()) throw new IllegalArgumentException("approvalThresholdMinor must be <= perTxCapMinor");
    if(p.approvalTtlSeconds()<60||p.approvalTtlSeconds()>86400) throw new IllegalArgumentException("approvalTtlSeconds out of range");
  }
  public String explain(List<String> codes){ return "Policy decision: "+String.join(", ", codes); }
}
