package co.tz.kikomo.db;

import co.tz.kikomo.shared.AgentStatus;
import co.tz.kikomo.shared.Hashing;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AgentRepository {
    public static final String DB_SELECT_STMT = "SELECT ";
    private static final String COLUMNS = "id, name, status, created_at";
    private static final RowMapper<Agent> MAPPER = (rs, i) -> new Agent(
            rs.getObject("id", UUID.class),
            rs.getString("name"),
            AgentStatus.fromValue(rs.getString("status")),
            rs.getObject("created_at", OffsetDateTime.class));
    private final JdbcTemplate jdbc;

    public AgentRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Bearer tokens are stored only as SHA-256 hashes.
     */
    public static String hashToken(String token) {
        return Hashing.sha256Hex(token);
    }

    public Optional<Agent> findByToken(String token) {
        return jdbc.query(DB_SELECT_STMT + COLUMNS + " FROM agents WHERE token_hash = ?", MAPPER, hashToken(token)).stream().findFirst();
    }

    public Optional<Agent> findById(UUID id) {
        return jdbc.query(DB_SELECT_STMT + COLUMNS + " FROM agents WHERE id = ?", MAPPER, id).stream().findFirst();
    }

    public List<Agent> findAll() {
        return jdbc.query(DB_SELECT_STMT + COLUMNS + " FROM agents ORDER BY created_at", MAPPER);
    }

    public UUID insert(String name, String token, AgentStatus status) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO agents(id, name, token_hash, status) VALUES (?, ?, ?, ?)", id, name, hashToken(token), status.value());
        return id;
    }

    /**
     * @return true if the agent exists
     */
    public boolean updateStatus(UUID id, AgentStatus status) {
        return jdbc.update("UPDATE agents SET status = ? WHERE id = ?", status.value(), id) == 1;
    }

    /**
     * Serialises evaluation for one agent until the surrounding transaction ends, so concurrent calls cannot
     * all pass the budget/velocity checks before any of them is recorded. Must run inside a transaction.
     */
    public void lockForEvaluation(UUID agentId) {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", agentId.toString());
    }
}
