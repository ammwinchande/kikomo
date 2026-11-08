package co.tz.kikomo.db;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** Executed spend that counts against the daily budget. */
@Repository
public class LedgerRepository {
    private final JdbcTemplate jdbc;

    public LedgerRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public long spentToday(UUID agentId) {
        Long sum = jdbc.queryForObject("SELECT COALESCE(SUM(amount_minor), 0) FROM spend_ledger WHERE agent_id = ? AND created_at >= date_trunc('day', now())",
                Long.class, agentId);
        return sum == null ? 0 : sum;
    }

    /** Idempotent per call (unique index on call_id). */
    public void record(UUID agentId, UUID callId, long amountMinor, String currency, String counterparty) {
        jdbc.update("""
                        INSERT INTO spend_ledger(agent_id, call_id, amount_minor, currency, counterparty) VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT (call_id) DO NOTHING""",
                agentId, callId, amountMinor, currency, counterparty);
    }
}
