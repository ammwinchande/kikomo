package co.tz.kikomo.config;

import co.tz.kikomo.db.Agent;
import co.tz.kikomo.db.AgentRepository;
import co.tz.kikomo.db.PolicyRepository;
import co.tz.kikomo.shared.AgentStatus;
import co.tz.kikomo.shared.Policy;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Seeds the demo agent (DEMO_AGENT_TOKEN) and its demo policy (SPEC 6.1) if missing. Idempotent.
 */
@Component
public class SeedRunner implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);
    private static final String DEMO_AGENT_NAME = "demo-agent";

    private final AgentRepository agents;
    private final PolicyRepository policies;
    private final AppConfig.Demo demo;

    public SeedRunner(AgentRepository agents, PolicyRepository policies, AppConfig config) {
        this.agents = agents;
        this.policies = policies;
        this.demo = config.demo();
    }

    @Override
    public void run(String @NonNull ... args) {
        try {
            UUID agentId = agents.findByToken(demo.agentToken())
                    .map(Agent::id)
                    .orElseGet(() -> agents.insert(DEMO_AGENT_NAME, demo.agentToken(), AgentStatus.ACTIVE));
            if (policies.findActive(agentId).isEmpty()) {
                policies.save(agentId, Policy.demo(List.of(demo.payeeDevtools(), demo.payeeConf())), "demo policy", "active");
            }
            log.info("Demo agent ready: {}", agentId);
        } catch (DataAccessException e) {
            // Seeding is a convenience; the gateway works without it, so do not stop startup.
            log.error("Demo seed failed", e);
        }
    }
}
