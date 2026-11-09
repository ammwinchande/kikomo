package co.tz.kikomo.integration;

import co.tz.kikomo.audit.AuditService;
import co.tz.kikomo.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hash-chained audit log against real Postgres.
 */
class AuditIntTest extends IntegrationTestBase {
    @Autowired
    AuditService audit;
    
    @Test
    void concurrentAppendsKeepTheChainValid() throws Exception {
        UUID agentId = newAgent().id();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (int i = 0; i < 25; i++) {
                int n = i;
                pool.submit(() -> audit.appendQuietly(agentId, "test_event", Map.of("n", n)));
            }
            pool.shutdown();
            assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        }
        assertEquals(25, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE agent_id=?", Integer.class, agentId));
        assertTrue(audit.verify());
    }

    @Test
    void tamperingIsDetected() {
        UUID agentId = newAgent().id();
        audit.append(agentId, "test_event", Map.of("x", 1));
        assertTrue(audit.verify());
        jdbc.execute("ALTER TABLE audit_log DISABLE TRIGGER audit_immutable");
        try {
            jdbc.update("UPDATE audit_log SET payload = '{\"x\":2}'::jsonb WHERE agent_id = ?", agentId);
            assertFalse(audit.verify());
        } finally {
            jdbc.update("DELETE FROM audit_log WHERE agent_id = ?", agentId); // remove the tampered tail
            jdbc.execute("ALTER TABLE audit_log ENABLE TRIGGER audit_immutable");
        }
    }

    @Test
    void auditLogIsAppendOnly() {
        UUID agentId = newAgent().id();
        audit.append(agentId, "test_event", Map.of("x", 1));
        org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,
                () -> jdbc.update("DELETE FROM audit_log WHERE agent_id = ?", agentId));
    }
}
