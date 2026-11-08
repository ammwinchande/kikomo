package co.tz.kikomo.config;

import co.tz.kikomo.db.AgentRepository;
import co.tz.kikomo.mcp.AgentTokenFilter;
import co.tz.kikomo.shared.error.NotFoundException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Two kinds of clients: the admin SPA (signed session cookie + double-submit CSRF token on {@code /api/**}) and
 * agents (Bearer token on {@code /mcp}, checked by {@link AgentTokenFilter}).
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain chain(HttpSecurity http, AdminSession session, AgentRepository agents,
                              @Value("${spring.ai.mcp.server.streamable-http.mcp-endpoint:/mcp}") String mcpEndpoint) {
        // The SPA reads XSRF-TOKEN and echoes it in the X-XSRF-TOKEN header. The cookie must be readable by
        // JavaScript for the double-submit pattern, so it is intentionally not HttpOnly.
        CookieCsrfTokenRepository csrfRepo = CookieCsrfTokenRepository.withHttpOnlyFalse(); // NOSONAR java:S3330 see above
        csrfRepo.setCookieCustomizer(c -> c.path("/").sameSite("Strict").secure(session.isSecureCookies()));
        // Plain (non-XOR) handler: the SPA sends the raw cookie value back in the header.
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();

        http.csrf(c -> c.csrfTokenRepository(csrfRepo)
                        .csrfTokenRequestHandler(csrfHandler)
                        // /mcp authenticates with an Authorization: Bearer header, which browsers never send
                        // ambiently, so CSRF does not apply there.
                        .ignoringRequestMatchers(mcpEndpoint)) // NOSONAR java:S4502 bearer-token endpoint, see above
                .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
                .addFilterBefore(new AgentTokenFilter(agents, mcpEndpoint), UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(new AdminCookieFilter(session), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/login", "/api/health").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable);
        return http.build();
    }

    /**
     * No username/password users exist (admin uses the signed session cookie, agents use bearer tokens). Declaring
     * this bean stops Spring Boot from generating a default user and printing its password at startup.
     */
    @Bean
    UserDetailsService noUserDetails() {
        return username -> {
            throw new NotFoundException("no form-login users");
        };
    }

    /**
     * Loads the deferred CSRF token so the XSRF-TOKEN cookie is written on every response.
     */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
            CsrfToken token = (CsrfToken) req.getAttribute(CsrfToken.class.getName());
            if (token != null) token.getToken();
            chain.doFilter(req, res);
        }
    }

    static final class AdminCookieFilter extends OncePerRequestFilter {
        private final AdminSession session;

        AdminCookieFilter(AdminSession session) {
            this.session = session;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
            Cookie[] cookies = req.getCookies();
            if (cookies != null) {
                for (Cookie c : cookies) {
                    if (AdminSession.COOKIE_NAME.equals(c.getName()) && session.verify(c.getValue())) {
                        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated("admin", null, List.of()));
                        break;
                    }
                }
            }
            chain.doFilter(req, res);
        }
    }
}
