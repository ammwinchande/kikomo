package co.tz.kikomo.audit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AuditTest {
    @Test
    void chainVerifies() {
        var chain = AuditChain.build(List.of(new String[]{"a", "{\"x\":1}"}, new String[]{"b", "{\"y\":2}"}));
        assertTrue(AuditChain.verify(chain));
        assertEquals(AuditChain.GENESIS_HASH, chain.getFirst().prevHash());
        assertEquals("0".repeat(64), AuditChain.GENESIS_HASH);
    }

    @Test
    void tamperDetected() {
        var chain = new ArrayList<>(AuditChain.build(List.of(new String[]{"a", "{}"}, new String[]{"b", "{}"})));
        chain.set(1, new AuditChain.Entry(2, chain.get(1).prevHash(), "f".repeat(64), "b", "{}"));
        assertFalse(AuditChain.verify(chain));
    }

    @Test
    void stableStringifySortsNestedKeys() {
        assertEquals("{\"a\":1,\"b\":{\"x\":[1,{\"m\":1,\"n\":2}],\"y\":2}}",
                AuditChain.stableStringify(Map.of("b", Map.of("y", 2, "x", List.of(1, Map.of("n", 2, "m", 1))), "a", 1)));
    }

    @Test
    void sha256Known() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", co.tz.kikomo.shared.Hashing.sha256Hex(""));
    }
}
