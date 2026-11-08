package co.tz.kikomo.db;

import co.tz.kikomo.shared.ApprovalStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Approval(UUID id, UUID callId, UUID agentId, ApprovalStatus status, String decidedBy,
                       OffsetDateTime decidedAt, OffsetDateTime expiresAt, OffsetDateTime createdAt) {
    public boolean isExpiredAt(OffsetDateTime now) {
        return !expiresAt.isAfter(now);
    }
}
