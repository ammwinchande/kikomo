package co.tz.kikomo.web;

import co.tz.kikomo.audit.AuditEvent;
import co.tz.kikomo.audit.AuditService;
import co.tz.kikomo.db.AgentRepository;
import co.tz.kikomo.db.PolicyRepository;
import co.tz.kikomo.llm.CompilePolicy;
import co.tz.kikomo.shared.Policy;
import co.tz.kikomo.shared.PolicyValidator;
import co.tz.kikomo.shared.error.NotFoundException;
import co.tz.kikomo.web.dto.CompileRequest;
import co.tz.kikomo.web.dto.CompileResponse;
import co.tz.kikomo.web.dto.OkResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/policies")
public class PolicyController {
    private static final Logger log = LoggerFactory.getLogger(PolicyController.class);

    private final PolicyRepository policies;
    private final AgentRepository agents;
    private final CompilePolicy compiler;
    private final PolicyValidator validator;
    private final AuditService audit;

    public PolicyController(PolicyRepository policies, AgentRepository agents, CompilePolicy compiler, PolicyValidator validator,
                            AuditService audit) {
        this.policies = policies;
        this.agents = agents;
        this.compiler = compiler;
        this.validator = validator;
        this.audit = audit;
    }

    @GetMapping("/{agentId}")
    public Policy active(@PathVariable("agentId") UUID agentId) {
        return policies.findActive(agentId).orElseThrow(() -> new NotFoundException("no active policy for agent " + agentId));
    }

    /**
     * Compiles English text to a DRAFT policy. Never activates anything.
     */
    @PostMapping("/compile")
    public CompileResponse compile(@RequestBody CompileRequest request) {
        String text = request.text() == null ? "" : request.text();
        CompilePolicy.CompiledPolicy compiled = compiler.compile(text, request.allowlist());
        return new CompileResponse(compiled.draft(), text, compiled.source().name().toLowerCase(Locale.ROOT));
    }

    /**
     * Validates and activates a policy (previous version is archived). 400 if invalid, 404 if the agent is unknown.
     */
    @PostMapping("/{agentId}/activate")
    @Transactional
    public OkResponse activate(@PathVariable("agentId") UUID agentId, @Valid @RequestBody Policy policy) {
        validator.validate(policy);
        agents.findById(agentId).orElseThrow(() -> new NotFoundException("agent " + agentId + " not found"));
        policies.activate(agentId, policy, null);
        audit.append(agentId, AuditEvent.POLICY_ACTIVATED, Map.of("agent_id", agentId.toString(), "per_tx_cap_minor", policy.perTxCapMinor(),
                "daily_budget_minor", policy.dailyBudgetMinor()));
        log.info("Policy activated for agent {}", agentId);
        return OkResponse.OK_RESPONSE;
    }
}
