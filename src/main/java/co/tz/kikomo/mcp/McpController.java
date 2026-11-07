package co.tz.kikomo.mcp;
import co.tz.kikomo.db.Repositories; import co.tz.kikomo.engine.Normalize; import co.tz.kikomo.gateway.GatewayManager; import com.fasterxml.jackson.databind.*;
import org.springframework.beans.factory.annotation.Value; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
import java.net.*; import java.net.http.*; import java.util.*; import java.util.regex.*;
@RestController
public class McpController {
  private final Repositories repo; private final GatewayManager gateway; private final ObjectMapper mapper=new ObjectMapper();
  @Value("${kikomo.fetch-allowed-hosts:localhost,127.0.0.1}") String allowedHostsRaw;
  public McpController(Repositories repo, GatewayManager gateway){ this.repo=repo; this.gateway=gateway; }
  @PostMapping("/mcp") public ResponseEntity<JsonNode> handle(@RequestHeader(value="Authorization",required=false) String auth, @RequestBody JsonNode body){
    if(auth==null||!auth.startsWith("Bearer ")) return ResponseEntity.status(401).body(err(body,"unauthorized"));
    var agentOpt=repo.findAgentByToken(auth.substring(7).trim());
    if(agentOpt.isEmpty()) return ResponseEntity.status(401).body(err(body,"invalid token"));
    UUID agentId=(UUID)agentOpt.get().get("id");
    String method=body.path("method").asText("");
    JsonNode idNode=body.path("id");
    try{
      if("initialize".equals(method)) return ok(idNode, Map.of("protocolVersion","2024-11-05","capabilities",Map.of("tools",Map.of()),"serverInfo",Map.of("name","kikomo","version","0.1.0")));
      if("notifications/initialized".equals(method)) return ResponseEntity.ok().build();
      if("tools/list".equals(method)) return ok(idNode, Map.of("tools", toolsList()));
      if("tools/call".equals(method)){
        String tool=body.path("params").path("name").asText();
        JsonNode argsNode=body.path("params").path("arguments"); Map<String,Object> args=mapper.convertValue(argsNode.isMissingNode()?Map.of():argsNode, Map.class);
        Map<String,Object> result;
        if("kikomo_fetch_url".equals(tool)) result=fetchUrl(agentId, args);
        else result=gateway.execute(agentId, tool, args);
        String text=mapper.writeValueAsString(result);
        return ok(idNode, Map.of("content",List.of(Map.of("type","text","text",text)),"isError",false,"structuredContent",result));
      }
      return ok(idNode, Map.of());
    }catch(Exception e){ return ResponseEntity.ok(err(body, e.getMessage())); }
  }
  @GetMapping("/mcp") public Map<String,Object> info(){ return Map.of("name","kikomo","transport","streamable-http","tools",toolsList()); }
  private List<Map<String,Object>> toolsList(){
    List<Map<String,Object>> t=new ArrayList<>();
    for(String name: List.of("send_payment","create_order","get_order","create_invoice","create_refund","list_transactions","check_approval","kikomo_fetch_url")){
      t.add(Map.of("name",name,"description","Kikomo gateway tool "+name,"inputSchema",Map.of("type","object","properties",Map.of())));
    } return t;
  }
  private Map<String,Object> fetchUrl(UUID agentId, Map<String,Object> args){
    String url=String.valueOf(args.get("url"));
    try{
      URI uri=URI.create(url); String host=uri.getHost();
      Set<String> allowed=new HashSet<>(Arrays.asList(allowedHostsRaw.split(","))); allowed.add("localhost"); allowed.add("127.0.0.1");
      // also allow host of PUBLIC_URL handled via allowedHostsRaw config
      if(host==null||!allowed.contains(host.trim())) return Map.of("status","denied","decision","deny","reason_codes",List.of("SSRF_BLOCKED"),"explanation","Host not in FETCH_ALLOWED_HOSTS.");
      var res=HttpClient.newHttpClient().send(java.net.http.HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
      String text=res.body(); var m=Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(text);
      List<String> found=new ArrayList<>(); while(m.find()){ String n=Normalize.payee(m.group()); found.add(n); repo.addUntrusted(agentId,n,url); }
      return Map.of("status","executed","decision","allow","reason_codes",List.of("OK_WITHIN_POLICY"),"fetched_url",url,"emails_found",found,"paypal",Map.of("mock",gateway.paypal().isMock()));
    }catch(Exception e){ return Map.of("status","failed","error",String.valueOf(e.getMessage())); }
  }
  private ResponseEntity<JsonNode> ok(JsonNode id, Object result){ try{ Map<String,Object> r=new LinkedHashMap<>(); r.put("jsonrpc","2.0"); if(id!=null&&!id.isMissingNode()) r.put("id", mapper.convertValue(id,Object.class)); r.put("result",result); return ResponseEntity.ok(mapper.valueToTree(r)); }catch(Exception e){ throw new RuntimeException(e);} }
  private JsonNode err(JsonNode body,String msg){ Map<String,Object> r=new LinkedHashMap<>(); r.put("jsonrpc","2.0"); r.put("id", body!=null&&body.has("id")?mapper.convertValue(body.get("id"),Object.class):null); r.put("error",Map.of("code",-32000,"message",msg==null?"error":msg)); return mapper.valueToTree(r); }
}
