package co.tz.kikomo.db;
import com.fasterxml.jackson.databind.ObjectMapper; import co.tz.kikomo.shared.Policy; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Repository;
import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.util.*;
@Repository
public class Repositories {
  static final String INSERT_CALL_SQL = "INSERT INTO calls(id,agent_id,tool,args,counterparty,category,decision,reason_codes,status,amount_minor,currency) VALUES (?,?,?,?::jsonb,?,?,?,?::text[],?,?,?)";
  private final JdbcTemplate jdbc; private final ObjectMapper mapper=new ObjectMapper();
  public Repositories(JdbcTemplate jdbc){ this.jdbc=jdbc; }
  public static String hashToken(String token){ try{ return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }catch(Exception e){ throw new RuntimeException(e);} }
  public Optional<Map<String,Object>> findAgentByToken(String token){
    var rows=jdbc.queryForList("SELECT id,name,status FROM agents WHERE token_hash=?", hashToken(token));
    return rows.isEmpty()?Optional.empty():Optional.of(rows.get(0));
  }
  public Optional<Map<String,Object>> findAgentById(UUID id){ var r=jdbc.queryForList("SELECT id,name,status FROM agents WHERE id=?", id); return r.isEmpty()?Optional.empty():Optional.of(r.get(0)); }
  public List<Map<String,Object>> listAgents(){ return jdbc.queryForList("SELECT id,name,status,created_at FROM agents ORDER BY created_at"); }
  public void setAgentStatus(UUID id,String status){ jdbc.update("UPDATE agents SET status=? WHERE id=?", status, id); }
  public Policy activePolicy(UUID agentId){
    var rows=jdbc.queryForList("SELECT doc::text AS d FROM policies WHERE agent_id=? AND status='active'", agentId);
    if(rows.isEmpty()) return null;
    try{ return mapper.readValue((String)rows.get(0).get("d"), Policy.class); }catch(Exception e){ throw new RuntimeException(e); }
  }
  public long spentToday(UUID agentId){ Long v=jdbc.queryForObject("SELECT COALESCE(SUM(amount_minor),0) FROM spend_ledger WHERE agent_id=? AND created_at >= date_trunc('day', now())", Long.class, agentId); return v==null?0:v; }
  public int callsInWindow(UUID agentId,int windowSeconds){ Integer v=jdbc.queryForObject("SELECT COUNT(*) FROM calls WHERE agent_id=? AND created_at >= now() - (? * interval '1 second')", Integer.class, agentId, windowSeconds); return v==null?0:v; }
  public Set<String> untrusted(UUID agentId){ return new HashSet<>(jdbc.queryForList("SELECT value_norm FROM untrusted_strings WHERE agent_id=?", String.class, agentId)); }
  public void addUntrusted(UUID agentId,String valueNorm,String url){ jdbc.update("INSERT INTO untrusted_strings(agent_id,value_norm,source_url) VALUES (?,?,?) ON CONFLICT (agent_id,value_norm) DO NOTHING", agentId, valueNorm, url); }
  public UUID insertCall(UUID agentId,String tool,String argsJson,Long amount,String currency,String counterparty,String category,String decision,List<String> reasons,String status){
    UUID id=UUID.randomUUID();
    jdbc.update(INSERT_CALL_SQL,
      id, agentId, tool, argsJson, counterparty, category, decision, reasons.toArray(new String[0]), status, amount, currency);
    return id;
  }
  public void updateCall(UUID id,String status,String resultJson,UUID approvalId){
    if(approvalId!=null) jdbc.update("UPDATE calls SET status=?, result=?::jsonb, approval_id=? WHERE id=?", status, resultJson, approvalId, id);
    else if(resultJson!=null) jdbc.update("UPDATE calls SET status=?, result=?::jsonb WHERE id=?", status, resultJson, id);
    else jdbc.update("UPDATE calls SET status=? WHERE id=?", status, id);
  }
  public Map<String,Object> getCall(UUID id){ var r=jdbc.queryForList("SELECT * FROM calls WHERE id=?", id); return r.isEmpty()?null:r.get(0); }
  public List<Map<String,Object>> listCalls(UUID agentId){ if(agentId==null) return jdbc.queryForList("SELECT * FROM calls ORDER BY created_at DESC LIMIT 100"); return jdbc.queryForList("SELECT * FROM calls WHERE agent_id=? ORDER BY created_at DESC LIMIT 100", agentId); }
  public UUID insertApproval(UUID callId,UUID agentId,int ttlSeconds){ UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO approvals(id,call_id,agent_id,expires_at) VALUES (?,?,?, now() + (? * interval '1 second'))", id, callId, agentId, ttlSeconds); return id; }
  public Map<String,Object> getApproval(UUID id){ var r=jdbc.queryForList("SELECT * FROM approvals WHERE id=?", id); return r.isEmpty()?null:r.get(0); }
  public List<Map<String,Object>> listApprovals(String status){ if(status==null) return jdbc.queryForList("SELECT * FROM approvals ORDER BY created_at DESC LIMIT 100"); return jdbc.queryForList("SELECT * FROM approvals WHERE status=? ORDER BY created_at DESC LIMIT 100", status); }
  public void decideApproval(UUID id,String decision,String by){ jdbc.update("UPDATE approvals SET status=?, decided_by=?, decided_at=now() WHERE id=? AND status='pending'", decision, by, id); }
  public void addSpend(UUID agentId,UUID callId,long amount,String currency,String counterparty){ jdbc.update("INSERT INTO spend_ledger(agent_id,call_id,amount_minor,currency,counterparty) VALUES (?,?,?,?,?)", agentId, callId, amount, currency, counterparty); }
  public void savePolicy(UUID agentId, Policy policy, String sourceText, String status){
    int next=1; Integer mx=jdbc.queryForObject("SELECT COALESCE(MAX(version),0) FROM policies WHERE agent_id=?", Integer.class, agentId); if(mx!=null) next=mx+1;
    try{ String json=mapper.writeValueAsString(policy);
      if("active".equals(status)) jdbc.update("UPDATE policies SET status='archived' WHERE agent_id=? AND status='active'", agentId);
      jdbc.update("INSERT INTO policies(agent_id,version,doc,source_text,status,confirmed_at) VALUES (?,?,?::jsonb,?,?, CASE WHEN ?='active' THEN now() ELSE NULL END)", agentId, next, json, sourceText, status, status);
    }catch(Exception e){ throw new RuntimeException(e); }
  }
}
