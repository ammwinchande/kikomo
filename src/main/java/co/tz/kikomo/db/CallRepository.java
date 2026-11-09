package co.tz.kikomo.db;

import co.tz.kikomo.shared.CallStatus;
import co.tz.kikomo.shared.Decision;
import co.tz.kikomo.shared.Tools;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class CallRepository {
    public static final String DB_SELECT_STMT = "SELECT ";
    static final String INSERT_SQL = """
            INSERT INTO calls(id, agent_id, tool, args, amount_minor, currency, counterparty, category, decision, reason_codes, status)
            VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?, ?::text[], ?)""";
    private static final String COLUMNS = "id, agent_id, tool, args::text AS args, amount_minor, currency, counterparty, category, "
            + "decision, reason_codes, status, approval_id, result::text AS result, created_at";
    private static final int LIST_LIMIT = 100;
    private static final RowMapper<CallRecord> MAPPER = CallRepository::map;
    private final JdbcTemplate jdbc;

    public CallRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static CallRecord map(ResultSet rs, int rowNum) throws SQLException {
        long amount = rs.getLong("amount_minor");
        Long amountMinor = rs.wasNull() ? null : amount;
        return new CallRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("agent_id", UUID.class),
                rs.getString("tool"),
                rs.getString("args"),
                amountMinor,
                rs.getString("currency"),
                rs.getString("counterparty"),
                rs.getString("category"),
                Decision.fromValue(rs.getString("decision")),
                textArray(rs.getArray("reason_codes")),
                CallStatus.fromValue(rs.getString("status")),
                rs.getObject("approval_id", UUID.class),
                rs.getString("result"),
                rs.getObject("created_at", OffsetDateTime.class));
    }

    private static List<String> textArray(Array array) throws SQLException {
        if (array == null) return List.of();
        try {
            return List.of((String[]) array.getArray());
        } finally {
            array.free();
        }
    }

    public UUID insert(NewCall call) {
        UUID id = UUID.randomUUID();
        jdbc.update(INSERT_SQL, id, call.agentId(), call.tool(), call.argsJson(), call.amountMinor(), call.currency(),
                call.counterparty(), call.category(), call.decision().value(), call.reasonCodes().toArray(new String[0]),
                call.status().value());
        return id;
    }

    public Optional<CallRecord> findById(UUID id) {
        return jdbc.query(DB_SELECT_STMT + COLUMNS + " FROM calls WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    /** The given calls in no particular order; unknown ids are skipped. */
    public List<CallRecord> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        UUID[] array = ids.toArray(new UUID[0]);
        return jdbc.query(con -> {
            var ps = con.prepareStatement(DB_SELECT_STMT + COLUMNS + " FROM calls WHERE id = ANY(?)");
            ps.setArray(1, con.createArrayOf("uuid", array));
            return ps;
        }, MAPPER);
    }

    /** Calls made since the start of the database day, grouped by decision (same day boundary as the daily budget). */
    public Map<Decision, Long> countTodayByDecision() {
        Map<Decision, Long> counts = new EnumMap<>(Decision.class);
        jdbc.query("SELECT decision, COUNT(*) AS n FROM calls WHERE created_at >= date_trunc('day', now()) GROUP BY decision",
                rs -> {
                    counts.put(Decision.fromValue(rs.getString("decision")), rs.getLong("n"));
                });
        return counts;
    }

    public List<CallRecord> findRecent(UUID agentIdOrNull) {
        if (agentIdOrNull == null) {
            return jdbc.query(DB_SELECT_STMT + COLUMNS + " FROM calls ORDER BY created_at DESC LIMIT " + LIST_LIMIT, MAPPER);
        }
        return jdbc.query(DB_SELECT_STMT + COLUMNS + " FROM calls WHERE agent_id = ? ORDER BY created_at DESC LIMIT " + LIST_LIMIT, MAPPER, agentIdOrNull);
    }

    public int countInWindow(UUID agentId, int windowSeconds) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM calls WHERE agent_id = ? AND created_at >= now() - (? * interval '1 second')",
                Integer.class, agentId, windowSeconds);
        return n == null ? 0 : n;
    }

    /**
     * Amount of spending calls that are allowed/approved but still executing (not yet in the ledger).
     */
    public long reservedToday(UUID agentId) {
        Long sum = jdbc.queryForObject("""
                        SELECT COALESCE(SUM(amount_minor), 0) FROM calls
                        WHERE agent_id = ? AND status = 'executing' AND tool = ANY(?::text[]) AND created_at >= date_trunc('day', now())""",
                Long.class, agentId, Tools.SPENDING.toArray(new String[0]));
        return sum == null ? 0 : sum;
    }

    public void attachApproval(UUID callId, UUID approvalId) {
        jdbc.update("UPDATE calls SET approval_id = ? WHERE id = ?", approvalId, callId);
    }

    /**
     * Compare-and-set status transition. Only one caller can win a given transition, which is what makes
     * executing an approved call idempotent.
     *
     * @return true if this caller performed the transition
     */
    public boolean transition(UUID callId, CallStatus from, CallStatus to) {
        return jdbc.update("UPDATE calls SET status = ? WHERE id = ? AND status = ?", to.value(), callId, from.value()) == 1;
    }

    /**
     * Records the final outcome of an executing call.
     */
    public boolean complete(UUID callId, CallStatus outcome, String resultJson) {
        return jdbc.update("UPDATE calls SET status = ?, result = ?::jsonb WHERE id = ? AND status = 'executing'",
                outcome.value(), resultJson, callId) == 1;
    }
}
