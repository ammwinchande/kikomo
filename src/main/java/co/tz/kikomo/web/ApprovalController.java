package co.tz.kikomo.web;

import co.tz.kikomo.db.ApprovalRepository;
import co.tz.kikomo.gateway.ApprovalService;
import co.tz.kikomo.shared.ApprovalStatus;
import co.tz.kikomo.web.dto.ApprovalView;
import co.tz.kikomo.web.dto.OkResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Human approval inbox.
 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    /**
     * Single shared admin account (SPEC section 2).
     */
    static final String DECIDED_BY = "admin";

    private final ApprovalRepository approvals;
    private final ApprovalService approvalService;

    public ApprovalController(ApprovalRepository approvals, ApprovalService approvalService) {
        this.approvals = approvals;
        this.approvalService = approvalService;
    }

    /**
     * @param status optional filter: pending, approved, denied or expired
     */
    @GetMapping
    public List<ApprovalView> list(@RequestParam(value = "status", required = false) String status) {
        ApprovalStatus filter = status == null ? null : ApprovalStatus.fromValue(status);
        return approvals.findRecent(filter).stream().map(ApprovalView::of).toList();
    }

    /**
     * Approves and immediately executes the call. 404 if unknown, 409 if already decided or expired.
     */
    @PostMapping("/{id}/approve")
    public OkResponse approve(@PathVariable("id") UUID id) {
        approvalService.approve(id, DECIDED_BY);
        return OkResponse.OK_RESPONSE;
    }

    /**
     * 404 if unknown, 409 if already decided or expired.
     */
    @PostMapping("/{id}/deny")
    public OkResponse deny(@PathVariable("id") UUID id) {
        approvalService.deny(id, DECIDED_BY);
        return OkResponse.OK_RESPONSE;
    }
}
