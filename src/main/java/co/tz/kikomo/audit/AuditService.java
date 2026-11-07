package co.tz.kikomo.audit;
import com.fasterxml.jackson.databind.*; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets; import java.security.MessageDigest; import java.util.*;
@Service
public class AuditService {
  private final JdbcTemplate jdbc; private final ObjectMapper mapper=new ObjectMapper();
  public AuditService(JdbcTemplate jdbc){ this.jdbc=jdbc; }
  public static String stableStringify(Object node){
    try{ ObjectMapper m=new ObjectMapper(); m.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);
      JsonNode n=m.valueToTree(node); return writeSorted(m,n); }catch(Exception e){ throw new RuntimeException(e); }
  }
  private static String writeSorted(ObjectMapper m, JsonNode n) throws Exception {
    if(n.isObject()){ List<String> keys=new ArrayList<>(); n.fieldNames().forEachRemaining(keys::add); Collections.sort(keys);
      StringBuilder sb=new StringBuilder("{"); boolean first=true;
      for(String k:keys){ if(!first) sb.append(","); first=false; sb.append(m.writeValueAsString(k)).append(":").append(writeSorted(m,n.get(k))); } return sb.append("}").toString(); }
    if(n.isArray()){ StringBuilder sb=new StringBuilder("["); boolean first=true; for(JsonNode e:n){ if(!first) sb.append(","); first=false; sb.append(writeSorted(m,e)); } return sb.append("]").toString(); }
    return m.writeValueAsString(n);
  }
  public static String sha256(String s){ try{ var d=MessageDigest.getInstance("SHA-256"); return HexFormat.of().formatHex(d.digest(s.getBytes(StandardCharsets.UTF_8))); }catch(Exception e){ throw new RuntimeException(e);} }
  @Transactional public long append(UUID agentId, String eventType, Object payload){
    jdbc.queryForObject("SELECT pg_advisory_xact_lock(424242)", Integer.class);
    List<Map<String,Object>> last=jdbc.queryForList("SELECT seq, hash FROM audit_log ORDER BY seq DESC LIMIT 1");
    long seq=1; String prev="0".repeat(64);
    if(!last.isEmpty()){ seq=((Number)last.get(0).get("seq")).longValue()+1; prev=(String)last.get(0).get("hash"); }
    String payloadJson=stableStringify(payload);
    String hash=sha256(prev+"|"+seq+"|"+eventType+"|"+payloadJson);
    jdbc.update("INSERT INTO audit_log(seq,agent_id,event_type,payload,prev_hash,hash) VALUES (?,?,?,?,?::jsonb,?,?)", seq, agentId, eventType, payloadJson, prev, hash);
    return seq;
  }
  // In-memory chain for tests / H2 fallback
  public record Entry(long seq,String prevHash,String hash,String eventType,String payloadJson){}
  public static List<Entry> buildChain(List<String[]> events){
    List<Entry> out=new ArrayList<>(); String prev="0".repeat(64); long seq=1;
    for(String[] e: events){ String h=sha256(prev+"|"+seq+"|"+e[0]+"|"+e[1]); out.add(new Entry(seq,prev,h,e[0],e[1])); prev=h; seq++; } return out;
  }
  public static boolean verifyChain(List<Entry> chain){
    String prev="0".repeat(64);
    for(Entry e: chain){ if(!e.prevHash().equals(prev)) return false; if(!e.hash().equals(sha256(prev+"|"+e.seq()+"|"+e.eventType()+"|"+e.payloadJson()))) return false; prev=e.hash(); } return true;
  }
  public boolean verify(){
    try{ var rows=jdbc.queryForList("SELECT seq,event_type,payload::text AS p,prev_hash,hash FROM audit_log ORDER BY seq ASC");
      String prev="0".repeat(64);
      for(var r: rows){ long seq=((Number)r.get("seq")).longValue(); String ph=(String)r.get("prev_hash"); String h=(String)r.get("hash");
        if(!ph.equals(prev)) return false; String payloadJson=stableStringify(mapper.readTree((String)r.get("p")));
        // payload stored already sorted; recompute with stored text normalized
        String expected=sha256(prev+"|"+seq+"|"+r.get("event_type")+"|"+payloadJson);
        if(!expected.equals(h)) return false; prev=h; } return true;
    }catch(Exception e){ return false; }
  }
}
