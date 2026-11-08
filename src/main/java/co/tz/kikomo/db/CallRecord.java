package co.tz.kikomo.db;

import co.tz.kikomo.shared.CallStatus;
import co.tz.kikomo.shared.Decision;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** A row of {@code calls}. {@code argsJson}/{@code resultJson} are the raw jsonb text. */
public record CallRecord(UUID id, UUID agentId, String tool, String argsJson, Long amountMinor, String currency,
                         String counterparty, String category, Decision decision, List<String> reasonCodes,
                         CallStatus status, UUID approvalId, String resultJson, OffsetDateTime createdAt) {
}
