package co.tz.kikomo.db;

import co.tz.kikomo.shared.ApprovalStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ApprovalRepository {
    public static final String DB_SELECT_STMT = "SELECT ";
    private static final String COLUMNS = "id, call_id, agent_id, status, decided_by, decided_at, expires_at, created_at";
    private static final int LIST_LIMIT = 100;
    private static final RowMapper<Approval> MAPPER = (rs, i) -> new Approval(
            rs.getObject("id", UUID.class),
            rs.getObject("call_id", UUID.class),
            rs.getObject("agent_id", UUID.class),
            ApprovalStatus.fromValue(rs.getString("status")),
            rs.getString("decided_by"),
            rs.getObject("decided_at", OffsetDateTime.class),
            rs.getObject("expires_at", OffsetDateTime.class),
            rs.getObject("created_at", OffsetDateTime.class));
    private final JdbcTemplate jdbc;

    public ApprovalRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insert(UUID callId, UUID agentId, int ttlSeconds) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO approvals(id, call_id, agent_id, expires_at) VALUES (?, ?, ?, now() + (? * interval '1 second'))",
                id, callId, agentId, ttlSeconds);
        return id;
    }

    public Optional<Approval> findById(UUID id) {
        return jdbc.query(DB_SELECT_STMT + COLUMNS + " FROM approvals WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<Approval> findByCallId(UUID callId) {
        return jdbc.query(DB_SELECT_STMT + COLUMNS + " FROM approvals WHERE call_id = ?", MAPPER, callId).stream().findFirst();
    }

    public List<Approval> findRecent(ApprovalStatus statusOrNull) {
        if (statusOrNull == null) {
            return jdbc.query(DB_SELECT_STMT + COLUMNS + " FROM approvals ORDER BY created_at DESC LIMIT " + LIST_LIMIT, MAPPER);
        }
        return jdbc.query(DB_SELECT_STMT + COLUMNS + " FROM approvals WHERE status = ? ORDER BY created_at DESC LIMIT " + LIST_LIMIT,
                MAPPER, statusOrNull.value());
    }

    /**
     * Decides a still-pending, unexpired approval.
     *
     * @return true if this caller made the decision
     */
    public boolean decide(UUID id, ApprovalStatus decision, String decidedBy) {
        return jdbc.update("""
                        UPDATE approvals SET status = ?, decided_by = ?, decided_at = now()
                        WHERE id = ? AND status = 'pending' AND expires_at > now()""",
                decision.value(), decidedBy, id) == 1;
    }

    /**
     * Marks every overdue pending approval expired. @return the approvals that were expired
     */
    public List<Approval> expireOverdue() {
        return jdbc.query("""
                UPDATE approvals SET status = 'expired', decided_at = now()
                WHERE status = 'pending' AND expires_at <= now()
                RETURNING\s""" + COLUMNS, MAPPER);
    }

    /**
     * Expires one approval if it is pending and overdue. @return true if it was expired by this call
     */
    public boolean expireIfOverdue(UUID id) {
        return jdbc.update("UPDATE approvals SET status = 'expired', decided_at = now() WHERE id = ? AND status = 'pending' AND expires_at <= now()", id) == 1;
    }
}
