package co.tz.kikomo.web;

import co.tz.kikomo.audit.AuditService;
import co.tz.kikomo.web.dto.AuditVerifyResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/audit")
public class AuditController {
    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping("/verify")
    public AuditVerifyResponse verify() {
        boolean valid = audit.verify();
        return new AuditVerifyResponse(valid, valid);
    }

    /**
     * Kept for backwards compatibility; prefer {@code /api/audit/verify}.
     */
    @GetMapping
    public Map<String, Object> summary() {
        return Map.of("note", "use /api/audit/verify", "valid", audit.verify());
    }
}
