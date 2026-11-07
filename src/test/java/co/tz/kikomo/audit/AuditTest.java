package co.tz.kikomo.audit;
import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*; import java.util.*;
class AuditTest {
  @Test void chainVerifies(){ var c=AuditService.buildChain(List.of(new String[]{"a","{\"x\":1}"}, new String[]{"b","{\"y\":2}"})); assertTrue(AuditService.verifyChain(c)); assertEquals("0".repeat(64), c.get(0).prevHash()); }
  @Test void tamperDetected(){ var c=new ArrayList<>(AuditService.buildChain(List.of(new String[]{"a","{}"}, new String[]{"b","{}"}))); c.set(1,new AuditService.Entry(2,c.get(1).prevHash(),"f".repeat(64),"b","{}")); assertFalse(AuditService.verifyChain(c)); }
  @Test void stableStringifySorted(){ assertEquals("{\"a\":1,\"b\":2}", AuditService.stableStringify(Map.of("b",2,"a",1))); }
  @Test void sha256Known(){ assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", AuditService.sha256("")); }
}
