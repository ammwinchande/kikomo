package co.tz.kikomo.web;
import co.tz.kikomo.audit.AuditService; import co.tz.kikomo.db.Repositories; import co.tz.kikomo.gateway.GatewayManager; import co.tz.kikomo.llm.CompilePolicy; import co.tz.kikomo.shared.Policy;
import jakarta.servlet.http.*; import org.springframework.beans.factory.annotation.Value; import org.springframework.http.*; import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api")
public class ApiControllers {
  private final Repositories repo; private final GatewayManager gateway; private final AuditService audit; private final CompilePolicy compiler;
  @Value("${ADMIN_PASSWORD:admin}") String adminPassword;
  @Value("${kikomo.paypal-mode:mock}") String paypalMode;
  public ApiControllers(Repositories repo, GatewayManager gateway, AuditService audit, CompilePolicy compiler){ this.repo=repo; this.gateway=gateway; this.audit=audit; this.compiler=compiler; }
  @GetMapping("/health") public Map<String,Object> health(){ return Map.of("status","ok","paypal_mode",paypalMode, "mock", gateway.paypal().isMock()); }
  @PostMapping("/auth/login") public ResponseEntity<Map<String,Object>> login(@RequestBody Map<String,String> body, HttpServletResponse res){
    if(adminPassword.equals(body.get("password"))){ Cookie c=new Cookie("KIKOMO_ADMIN","ok-admin"); c.setPath("/"); c.setHttpOnly(true); res.addCookie(c); return ResponseEntity.ok(Map.of("ok",true)); }
    return ResponseEntity.status(401).body(Map.of("ok",false,"error","bad password"));
  }
  @PostMapping("/auth/logout") public Map<String,Object> logout(HttpServletResponse res){ Cookie c=new Cookie("KIKOMO_ADMIN",""); c.setPath("/"); c.setMaxAge(0); res.addCookie(c); return Map.of("ok",true); }
  @GetMapping("/agents") public Object agents(){ return repo.listAgents().stream().map(m->Map.of("id",String.valueOf(m.get("id")),"name",m.get("name"),"status",m.get("status"))).toList(); }
  @PostMapping("/agents/{id}/suspend") public Map<String,Object> suspend(@PathVariable("id") UUID id){ repo.setAgentStatus(id,"suspended"); safeAudit(id,"agent_suspended"); return Map.of("ok",true,"status","suspended"); }
  @PostMapping("/agents/{id}/activate") public Map<String,Object> activate(@PathVariable("id") UUID id){ repo.setAgentStatus(id,"active"); safeAudit(id,"agent_activated"); return Map.of("ok",true,"status","active"); }
  @GetMapping("/calls") public Object calls(@RequestParam(value="agentId", required=false) UUID agentId){ return repo.listCalls(agentId).stream().map(this::callView).toList(); }
  @GetMapping("/calls/{id}") public Object call(@PathVariable("id") UUID id){ var c=repo.getCall(id); return c==null?Map.of("error","not found"):callView(c); }
  @GetMapping("/approvals") public Object approvals(@RequestParam(value="status", required=false) String status){ return repo.listApprovals(status).stream().map(m->Map.of("id",String.valueOf(m.get("id")),"call_id",String.valueOf(m.get("call_id")),"agent_id",String.valueOf(m.get("agent_id")),"status",m.get("status"),"expires_at",String.valueOf(m.get("expires_at")))).toList(); }
  @PostMapping("/approvals/{id}/approve") public Map<String,Object> approve(@PathVariable("id") UUID id){ repo.decideApproval(id,"approved","admin"); var a=repo.getApproval(id); if(a!=null) repo.updateCall((UUID)a.get("call_id"),"pending_approval",null,null); safeAudit(null,"approval_approved"); return Map.of("ok",true); }
  @PostMapping("/approvals/{id}/deny") public Map<String,Object> deny(@PathVariable("id") UUID id){ repo.decideApproval(id,"denied","admin"); safeAudit(null,"approval_denied"); return Map.of("ok",true); }
  @GetMapping("/policies/{agentId}") public Object policy(@PathVariable("agentId") UUID agentId){ Policy p=repo.activePolicy(agentId); return p==null?Map.of("error","no active policy"):p; }
  @PostMapping("/policies/compile") public Object compile(@RequestBody Map<String,Object> body){
    String text=String.valueOf(body.getOrDefault("text",""));
    @SuppressWarnings("unchecked") List<String> allow=(List<String>)body.getOrDefault("allowlist", List.of());
    Policy draft=compiler.compile(text, allow); return Map.of("draft",draft,"source_text",text);
  }
  @PostMapping("/policies/{agentId}/activate") public Map<String,Object> activatePolicy(@PathVariable("agentId") UUID agentId, @RequestBody Policy policy){ CompilePolicy.validate(policy); repo.savePolicy(agentId, policy, null, "active"); safeAudit(agentId,"policy_activated"); return Map.of("ok",true); }
  @GetMapping("/audit/verify") public Map<String,Object> verify(){ boolean ok=audit.verify(); return Map.of("ok",ok,"valid",ok); }
  @GetMapping("/audit") public Map<String,Object> auditList(){ return Map.of("note","use /api/audit/verify","valid",audit.verify()); }
  private Map<String,Object> callView(Map<String,Object> m){ Map<String,Object> v=new LinkedHashMap<>(); v.put("id",String.valueOf(m.get("id"))); v.put("agent_id",String.valueOf(m.get("agent_id"))); v.put("tool",m.get("tool")); v.put("decision",m.get("decision")); v.put("status",m.get("status")); v.put("counterparty",m.get("counterparty")); v.put("amount_minor",m.get("amount_minor")); Object rc=m.get("reason_codes");
    if(rc instanceof java.sql.Array arr){ try{ v.put("reason_codes", java.util.List.of((String[])arr.getArray())); }catch(Exception e){ v.put("reason_codes", java.util.List.of()); } }
    else v.put("reason_codes", rc);
    return v; }
  private void safeAudit(UUID id,String t){ try{ audit.append(id,t,Map.of("at",java.time.Instant.now().toString())); }catch(Exception ignored){} }
}
