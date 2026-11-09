package co.tz.kikomo.web;

import co.tz.kikomo.audit.AuditService;
import co.tz.kikomo.db.ApprovalRepository;
import co.tz.kikomo.db.CallRepository;
import co.tz.kikomo.shared.Decision;
import co.tz.kikomo.web.dto.StatsView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Numbers for the inbox header tiles.
 */
@RestController
@RequestMapping("/api/stats")
public class StatsController {
    private final CallRepository calls;
    private final ApprovalRepository approvals;
    private final AuditService audit;

    public StatsController(CallRepository calls, ApprovalRepository approvals, AuditService audit) {
        this.calls = calls;
        this.approvals = approvals;
        this.audit = audit;
    }

    @GetMapping
    public StatsView stats() {
        Map<Decision, Long> today = calls.countTodayByDecision();
        return new StatsView(approvals.countPending(),
                today.getOrDefault(Decision.ALLOW, 0L),
                today.getOrDefault(Decision.DENY, 0L),
                today.getOrDefault(Decision.NEEDS_APPROVAL, 0L),
                audit.verify());
    }
}
