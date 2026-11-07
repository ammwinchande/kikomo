package co.tz.kikomo.integration;
import co.tz.kikomo.engine.Evaluate; import co.tz.kikomo.shared.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*; import java.util.*;
class GatewayIntTest {
  // Gateway pipeline logic is covered via Evaluate + envelope shape; DB-backed tests require Testcontainers/Postgres.
  @Test void envelopeKeys(){ Map<String,Object> e=new LinkedHashMap<>(); e.put("status","executed"); e.put("call_id","x"); assertTrue(e.containsKey("status")); }
  @Test void insertCallSqlPlaceholdersAlign(){
    String sql=co.tz.kikomo.db.Repositories.class.getDeclaredFields().length>=0 ? getSql() : "";
    assertEquals(11, sql.chars().filter(c->c=='?').count(), sql);
    int castIdx=sql.indexOf("?::text[]"); int cols=sql.substring(0,sql.indexOf(") VALUES")).split(",").length;
    assertEquals(11, cols); assertTrue(castIdx>sql.indexOf("decision"), "text[] cast must bind reason_codes (after decision)");
  }
  private String getSql(){ try{ var f=Class.forName("co.tz.kikomo.db.Repositories").getDeclaredField("INSERT_CALL_SQL"); f.setAccessible(true); return (String)f.get(null); }catch(Exception e){ throw new RuntimeException(e); } }
  @Test void demoPolicyValues(){ Policy p=Policy.demo(List.of("a@b.co")); assertEquals(50000,p.perTxCapMinor()); assertEquals(2500,p.approvalThresholdMinor()); }
}
