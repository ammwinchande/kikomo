package co.tz.kikomo.db;

import co.tz.kikomo.shared.Policy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Optional;
import java.util.UUID;

@Repository
public class PolicyRepository {
    static final String STATUS_ACTIVE = "active";

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    public PolicyRepository(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Optional<Policy> findActive(UUID agentId) {
        return jdbc.queryForList("SELECT doc::text FROM policies WHERE agent_id = ? AND status = 'active'", String.class, agentId)
                .stream().findFirst().map(doc -> json.readValue(doc, Policy.class));
    }

    /**
     * Stores a new policy version. Activating archives the previous active version in the same transaction, so
     * a failed insert never leaves the agent without a policy.
     */
    @Transactional
    public void save(UUID agentId, Policy policy, String sourceText, String status) {
        Integer maxVersion = jdbc.queryForObject("SELECT COALESCE(MAX(version), 0) FROM policies WHERE agent_id = ?", Integer.class, agentId);
        int nextVersion = (maxVersion == null ? 0 : maxVersion) + 1;
        if (STATUS_ACTIVE.equals(status)) {
            jdbc.update("UPDATE policies SET status = 'archived' WHERE agent_id = ? AND status = 'active'", agentId);
        }
        jdbc.update("""
                        INSERT INTO policies(agent_id, version, doc, source_text, status, confirmed_at)
                        VALUES (?, ?, ?::jsonb, ?, ?, CASE WHEN ? = 'active' THEN now() END)""",
                agentId, nextVersion, json.writeValueAsString(policy), sourceText, status, status);
    }

    public void activate(UUID agentId, Policy policy, String sourceText) {
        save(agentId, policy, sourceText, STATUS_ACTIVE);
    }
}
