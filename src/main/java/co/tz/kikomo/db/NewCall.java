package co.tz.kikomo.db;

import co.tz.kikomo.shared.CallStatus;
import co.tz.kikomo.shared.Decision;

import java.util.List;
import java.util.UUID;

/** Values for inserting a {@code calls} row. */
public record NewCall(UUID agentId, String tool, String argsJson, Long amountMinor, String currency,
                      String counterparty, String category, Decision decision, List<String> reasonCodes,
                      CallStatus status) {
}
