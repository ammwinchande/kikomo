package co.tz.kikomo.db;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Provenance: normalised strings an agent saw in fetched (untrusted) content. */
@Repository
public class UntrustedRepository {
    private final JdbcTemplate jdbc;

    public UntrustedRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Set<String> valuesFor(UUID agentId) {
        return new HashSet<>(jdbc.queryForList("SELECT value_norm FROM untrusted_strings WHERE agent_id = ?", String.class, agentId));
    }

    public void add(UUID agentId, String normalizedValue, String sourceUrl) {
        jdbc.update("INSERT INTO untrusted_strings(agent_id, value_norm, source_url) VALUES (?, ?, ?) ON CONFLICT (agent_id, value_norm) DO NOTHING",
                agentId, normalizedValue, sourceUrl);
    }
}
