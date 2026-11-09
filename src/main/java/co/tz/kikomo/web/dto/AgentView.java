package co.tz.kikomo.web.dto;

import co.tz.kikomo.db.Agent;
import co.tz.kikomo.shared.Policy;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** Agent plus today's spend against its active policy ({@code daily_budget_minor}/{@code policy_version} null without one). */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentView(String id, String name, String status, long spentTodayMinor, Long dailyBudgetMinor,
                        Integer policyVersion, String createdAt) {
    public static AgentView of(Agent agent, long spentTodayMinor, Policy activeOrNull) {
        return new AgentView(agent.id().toString(), agent.name(), agent.status().value(), spentTodayMinor,
                activeOrNull == null ? null : activeOrNull.dailyBudgetMinor(),
                activeOrNull == null ? null : activeOrNull.version(),
                agent.createdAt() == null ? null : agent.createdAt().toInstant().toString());
    }
}
