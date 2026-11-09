package co.tz.kikomo.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Append-only, hash-chained audit log (SPEC section 10).
 */
@Service
public class AuditService {
    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    /**
     * Global advisory lock serialising appends so seq/prev_hash stay consistent.
     */
    private static final long APPEND_LOCK_KEY = 424242L;
    private static final int VERIFY_FETCH_SIZE = 500;
    private static final String VERIFY_SQL = "SELECT seq, event_type, payload::text AS payload, prev_hash, hash FROM audit_log ORDER BY seq";

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final TransactionTemplate tx;

    public AuditService(JdbcTemplate jdbc, JsonMapper json, TransactionTemplate tx) {
        this.jdbc = jdbc;
        this.json = json;
        this.tx = tx;
    }

    /**
     * Appends an event. Joins the caller's transaction when there is one, so an event and the state change it
     * describes commit (or roll back) together.
     *
     * @return the sequence number of the new entry
     */
    public long append(UUID agentId, String eventType, Map<String, ?> payload) {
        // TransactionTemplate (not @Transactional) so the advisory lock is held even for internal calls such as
        // appendQuietly, which would bypass the Spring proxy.
        Long seq = tx.execute(status -> appendInTransaction(agentId, eventType, payload));
        return seq == null ? 0 : seq;
    }

    private long appendInTransaction(UUID agentId, String eventType, Map<String, ?> payload) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(?)", APPEND_LOCK_KEY);
        List<Map<String, Object>> last = jdbc.queryForList("SELECT seq, hash FROM audit_log ORDER BY seq DESC LIMIT 1");
        long seq = 1;
        String prevHash = AuditChain.GENESIS_HASH;
        if (!last.isEmpty()) {
            seq = ((Number) last.getFirst().get("seq")).longValue() + 1;
            prevHash = (String) last.getFirst().get("hash");
        }
        String payloadJson = AuditChain.stableStringify(payload);
        String hash = AuditChain.hash(prevHash, seq, eventType, payloadJson);
        jdbc.update("INSERT INTO audit_log(seq, agent_id, event_type, payload, prev_hash, hash) VALUES (?, ?, ?, ?::jsonb, ?, ?)",
                seq, agentId, eventType, payloadJson, prevHash, hash);
        return seq;
    }

    /**
     * Best-effort append for events recorded after money has already moved: a failure here must not turn a
     * successful payment into an error, so it is logged loudly instead of thrown.
     */
    public void appendQuietly(UUID agentId, String eventType, Map<String, ?> payload) {
        try {
            append(agentId, eventType, payload);
        } catch (DataAccessException | JacksonException e) {
            log.error("AUDIT APPEND FAILED event={} agent={} payload={}", eventType, agentId, payload, e);
        }
    }

    /**
     * Streams the whole chain in seq order and recomputes every hash.
     */
    @Transactional(readOnly = true)
    public boolean verify() {
        AtomicReference<String> prev = new AtomicReference<>(AuditChain.GENESIS_HASH);
        AtomicLong expectedSeq = new AtomicLong(1);
        AtomicBoolean valid = new AtomicBoolean(true);
        // Postgres streams rows (instead of loading the whole table) when a fetch size is set inside a transaction.
        jdbc.query(con -> {
            PreparedStatement ps = con.prepareStatement(VERIFY_SQL);
            ps.setFetchSize(VERIFY_FETCH_SIZE);
            return ps;
        }, rs -> {
            if (!valid.get()) return;
            long seq = rs.getLong("seq");
            String payloadJson = AuditChain.stableStringify(json.readTree(rs.getString("payload")));
            String expected = AuditChain.hash(prev.get(), seq, rs.getString("event_type"), payloadJson);
            if (seq != expectedSeq.getAndIncrement() || !prev.get().equals(rs.getString("prev_hash")) || !expected.equals(rs.getString("hash"))) {
                log.warn("Audit chain broken at seq={}", seq);
                valid.set(false);
                return;
            }
            prev.set(expected);
        });
        return valid.get();
    }
}
