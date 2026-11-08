package co.tz.kikomo.db;

import co.tz.kikomo.shared.AgentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record Agent(UUID id, String name, AgentStatus status, OffsetDateTime createdAt) {
}
