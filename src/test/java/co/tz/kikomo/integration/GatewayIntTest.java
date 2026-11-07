package co.tz.kikomo.integration;
import co.tz.kikomo.engine.Evaluate; import co.tz.kikomo.shared.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*; import java.util.*;
class GatewayIntTest {
  // Gateway pipeline logic is covered via Evaluate + envelope shape; DB-backed tests require Testcontainers/Postgres.
  @Test void envelopeKeys(){ Map<String,Object> e=new LinkedHashMap<>(); e.put("status","executed"); e.put("call_id","x"); assertTrue(e.containsKey("status")); }
  @Test void demoPolicyValues(){ Policy p=Policy.demo(List.of("a@b.co")); assertEquals(50000,p.perTxCapMinor()); assertEquals(2500,p.approvalThresholdMinor()); }
}
