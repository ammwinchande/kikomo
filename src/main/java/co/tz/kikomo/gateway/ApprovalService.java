package co.tz.kikomo.gateway;

import co.tz.kikomo.audit.AuditEvent;
import co.tz.kikomo.audit.AuditService;
import co.tz.kikomo.db.Approval;
import co.tz.kikomo.db.ApprovalRepository;
import co.tz.kikomo.db.CallRepository;
import co.tz.kikomo.shared.ApprovalStatus;
import co.tz.kikomo.shared.CallStatus;
import co.tz.kikomo.shared.error.ConflictException;
import co.tz.kikomo.shared.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Human approval decisions. Approving executes the call right away (exactly once, via a compare-and-set on the
 * call status), so a payment approved after the agent stopped waiting still runs; the agent sees the outcome
 * with check_approval.
 */
@Service
public class ApprovalService {
    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);
    private static final String KEY_APPROVAL_ID = "approval_id";
    private static final String KEY_CALL_ID = "call_id";
    private static final String KEY_DECIDED_BY = "decided_by";

    private final ApprovalRepository approvals;
    private final CallRepository calls;
    private final CallExecutor executor;
    private final AuditService audit;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ApprovalService(ApprovalRepository approvals, CallRepository calls, CallExecutor executor, AuditService audit,
                           TransactionTemplate tx, Clock clock) {
        this.approvals = approvals;
        this.calls = calls;
        this.executor = executor;
        this.audit = audit;
        this.tx = tx;
        this.clock = clock;
    }

    private static Map<String, String> payload(Approval approval, String decidedBy) {
        return Map.of(KEY_APPROVAL_ID, approval.id().toString(), KEY_CALL_ID, approval.callId().toString(), KEY_DECIDED_BY, decidedBy);
    }

    /**
     * @throws NotFoundException if the approval does not exist
     * @throws ConflictException if it is already decided or has expired
     */
    public void approve(UUID approvalId, String decidedBy) {
        Approval approval = requirePending(approvalId);
        Boolean claimed = tx.execute(status -> {
            if (!approvals.decide(approvalId, ApprovalStatus.APPROVED, decidedBy)) {
                throw new ConflictException("approval " + approvalId + " is no longer pending");
            }
            audit.append(approval.agentId(), AuditEvent.APPROVAL_APPROVED, payload(approval, decidedBy));
            return calls.transition(approval.callId(), CallStatus.PENDING_APPROVAL, CallStatus.EXECUTING);
        });
        log.info("Approval {} approved by {} (call {})", approvalId, decidedBy, approval.callId());
        if (Boolean.TRUE.equals(claimed)) {
            executor.execute(approval.callId());
        }
    }

    /**
     * @throws NotFoundException if the approval does not exist
     * @throws ConflictException if it is already decided or has expired
     */
    public void deny(UUID approvalId, String decidedBy) {
        Approval approval = requirePending(approvalId);
        tx.executeWithoutResult(status -> {
            if (!approvals.decide(approvalId, ApprovalStatus.DENIED, decidedBy)) {
                throw new ConflictException("approval " + approvalId + " is no longer pending");
            }
            calls.transition(approval.callId(), CallStatus.PENDING_APPROVAL, CallStatus.DENIED);
            audit.append(approval.agentId(), AuditEvent.APPROVAL_DENIED, payload(approval, decidedBy));
        });
        log.info("Approval {} denied by {} (call {})", approvalId, decidedBy, approval.callId());
    }

    /**
     * Expires the approval if it is pending and past its TTL. @return true if it was expired now
     */
    public boolean expireIfOverdue(Approval approval) {
        Boolean expired = tx.execute(status -> {
            if (!approvals.expireIfOverdue(approval.id())) return false;
            markExpired(approval);
            return true;
        });
        return Boolean.TRUE.equals(expired);
    }

    /**
     * Background sweep so overdue approvals show as expired even when nobody polls them.
     */
    @Scheduled(fixedDelayString = "${kikomo.approval.sweep-interval:30s}", initialDelayString = "${kikomo.approval.sweep-interval:30s}")
    public void expireOverdue() {
        tx.executeWithoutResult(status -> {
            List<Approval> expired = approvals.expireOverdue();
            expired.forEach(this::markExpired);
            if (!expired.isEmpty()) log.info("Expired {} overdue approval(s)", expired.size());
        });
    }

    private void markExpired(Approval approval) {
        calls.transition(approval.callId(), CallStatus.PENDING_APPROVAL, CallStatus.EXPIRED);
        audit.append(approval.agentId(), AuditEvent.APPROVAL_EXPIRED, Map.of(KEY_APPROVAL_ID, approval.id().toString(), KEY_CALL_ID, approval.callId().toString()));
    }

    private Approval requirePending(UUID approvalId) {
        Approval approval = approvals.findById(approvalId).orElseThrow(() -> new NotFoundException("approval " + approvalId + " not found"));
        if (approval.status() != ApprovalStatus.PENDING) {
            throw new ConflictException("approval " + approvalId + " is already " + approval.status().value());
        }
        if (approval.isExpiredAt(OffsetDateTime.now(clock))) {
            expireIfOverdue(approval);
            throw new ConflictException("approval " + approvalId + " has expired");
        }
        return approval;
    }
}
