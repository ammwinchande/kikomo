package co.tz.kikomo.web;

import co.tz.kikomo.audit.AuditEvent;
import co.tz.kikomo.audit.AuditService;
import co.tz.kikomo.db.AgentRepository;
import co.tz.kikomo.db.LedgerRepository;
import co.tz.kikomo.db.PolicyRepository;
import co.tz.kikomo.shared.AgentStatus;
import co.tz.kikomo.shared.error.NotFoundException;
import co.tz.kikomo.web.dto.AgentStatusResponse;
import co.tz.kikomo.web.dto.AgentView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agents and the kill switch.
 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {
    private static final Logger log = LoggerFactory.getLogger(AgentController.class);

    private final AgentRepository agents;
    private final PolicyRepository policies;
    private final LedgerRepository ledger;
    private final AuditService audit;

    public AgentController(AgentRepository agents, PolicyRepository policies, LedgerRepository ledger, AuditService audit) {
        this.agents = agents;
        this.policies = policies;
        this.ledger = ledger;
        this.audit = audit;
    }

    @GetMapping
    public List<AgentView> list() {
        return agents.findAll().stream()
                .map(a -> AgentView.of(a, ledger.spentToday(a.id()), policies.findActive(a.id()).orElse(null)))
                .toList();
    }

    @PostMapping("/{id}/suspend")
    @Transactional
    public AgentStatusResponse suspend(@PathVariable("id") UUID id) {
        return setStatus(id, AgentStatus.SUSPENDED, AuditEvent.AGENT_SUSPENDED);
    }

    @PostMapping("/{id}/activate")
    @Transactional
    public AgentStatusResponse activate(@PathVariable("id") UUID id) {
        return setStatus(id, AgentStatus.ACTIVE, AuditEvent.AGENT_ACTIVATED);
    }

    private AgentStatusResponse setStatus(UUID id, AgentStatus status, String auditEvent) {
        if (!agents.updateStatus(id, status)) {
            throw new NotFoundException("agent " + id + " not found");
        }
        audit.append(id, auditEvent, Map.of("agent_id", id.toString(), "status", status.value()));
        log.info("Agent {} set to {}", id, status.value());
        return new AgentStatusResponse(true, status.value());
    }
}
