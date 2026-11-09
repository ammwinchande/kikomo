package co.tz.kikomo.web.dto;

import co.tz.kikomo.db.Agent;

public record AgentView(String id, String name, String status) {
    public static AgentView of(Agent agent) {
        return new AgentView(agent.id().toString(), agent.name(), agent.status().value());
    }
}
