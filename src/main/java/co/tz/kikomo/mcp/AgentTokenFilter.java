package co.tz.kikomo.mcp;

import co.tz.kikomo.db.Agent;
import co.tz.kikomo.db.AgentRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * SPEC 7: authenticates MCP requests by Bearer token (SHA-256 looked up in {@code agents}). Unauthenticated
 * requests get HTTP 401 before reaching the MCP server; the agent id is passed on as a request attribute.
 */
public class AgentTokenFilter extends OncePerRequestFilter {
    public static final String AGENT_ID_ATTRIBUTE = AgentTokenFilter.class.getName() + ".agentId";
    private static final Logger log = LoggerFactory.getLogger(AgentTokenFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";

    private final AgentRepository agents;
    private final String mcpEndpoint;

    public AgentTokenFilter(AgentRepository agents, String mcpEndpoint) {
        this.agents = agents;
        this.mcpEndpoint = mcpEndpoint;
    }

    private static Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) return Optional.empty();
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !mcpEndpoint.equals(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain chain)
            throws ServletException, IOException {
        Optional<Agent> agent = bearerToken(request).flatMap(agents::findByToken);
        if (agent.isEmpty()) {
            log.warn("Rejected MCP request from {}: missing or invalid bearer token", request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32001,\"message\":\"unauthorized\"}}");
            return;
        }
        request.setAttribute(AGENT_ID_ATTRIBUTE, agent.get().id());
        chain.doFilter(request, response);
    }
}
