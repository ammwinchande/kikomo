package co.tz.kikomo.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStatelessServerTransport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

/**
 * Streamable HTTP (stateless) MCP transport at {@code /mcp}. Replaces Spring AI's default transport bean only to
 * add a context extractor that hands the authenticated agent id (set by {@link AgentTokenFilter}) to the tools.
 */
@Configuration
public class McpServerConfig {
    public static final String AGENT_ID_KEY = "kikomo.agentId";

    @Bean
    WebMvcStatelessServerTransport webMvcStatelessServerTransport(JsonMapper jsonMapper, McpServerStreamableHttpProperties properties) {
        return WebMvcStatelessServerTransport.builder()
                .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
                .messageEndpoint(properties.getMcpEndpoint())
                .contextExtractor(request -> {
                    Object agentId = request.servletRequest().getAttribute(AgentTokenFilter.AGENT_ID_ATTRIBUTE);
                    return agentId instanceof UUID id ? McpTransportContext.create(Map.of(AGENT_ID_KEY, id)) : McpTransportContext.EMPTY;
                })
                .build();
    }
}
