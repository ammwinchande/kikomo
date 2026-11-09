package co.tz.kikomo.audit;

import co.tz.kikomo.shared.Hashing;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure hash-chain rules (SPEC section 10), shared by {@link AuditService} and tests.
 */
public final class AuditChain {
    public static final String GENESIS_HASH = "0".repeat(64);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    
    private AuditChain() {
        /* utility class */
    }

    /**
     * {@code sha256(prev | seq | eventType | payloadJson)}.
     */
    public static String hash(String prevHash, long seq, String eventType, String payloadJson) {
        return Hashing.sha256Hex(prevHash + "|" + seq + "|" + eventType + "|" + payloadJson);
    }

    /**
     * Canonical JSON: object keys sorted recursively, no insignificant whitespace.
     */
    public static String stableStringify(Object value) {
        StringBuilder out = new StringBuilder();
        writeSorted(JSON.valueToTree(value), out);
        return out.toString();
    }

    private static void writeSorted(JsonNode node, StringBuilder out) {
        if (node.isObject()) {
            List<String> keys = new ArrayList<>(node.propertyNames());
            keys.sort(null);
            out.append('{');
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) out.append(',');
                out.append(JSON.writeValueAsString(keys.get(i))).append(':');
                writeSorted(node.get(keys.get(i)), out);
            }
            out.append('}');
        } else if (node.isArray()) {
            out.append('[');
            for (int i = 0; i < node.size(); i++) {
                if (i > 0) out.append(',');
                writeSorted(node.get(i), out);
            }
            out.append(']');
        } else {
            out.append(JSON.writeValueAsString(node));
        }
    }

    public static List<Entry> build(List<String[]> events) {
        List<Entry> chain = new ArrayList<>();
        String prev = GENESIS_HASH;
        long seq = 1;
        for (String[] event : events) {
            String hash = hash(prev, seq, event[0], event[1]);
            chain.add(new Entry(seq, prev, hash, event[0], event[1]));
            prev = hash;
            seq++;
        }
        return chain;
    }

    public static boolean verify(List<Entry> chain) {
        String prev = GENESIS_HASH;
        for (Entry e : chain) {
            if (!e.prevHash().equals(prev) || !e.hash().equals(hash(prev, e.seq(), e.eventType(), e.payloadJson()))) {
                return false;
            }
            prev = e.hash();
        }
        return true;
    }

    public record Entry(long seq, String prevHash, String hash, String eventType, String payloadJson) {
    }
}
