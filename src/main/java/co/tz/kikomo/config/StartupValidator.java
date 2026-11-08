package co.tz.kikomo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Fails fast on unsafe configuration. In live PayPal mode every secret SPEC section 4 marks as required must
 * be set (and must not be a published default); in mock mode the same problems are logged as warnings so
 * local development keeps working with zero configuration.
 */
@Component
public class StartupValidator implements InitializingBean {
    static final String DEFAULT_ADMIN_PASSWORD = "admin";
    static final String DEFAULT_DEMO_TOKEN = "demo-token-change-me-32chars!!";
    private static final Logger log = LoggerFactory.getLogger(StartupValidator.class);
    private static final int MIN_SECRET_LENGTH = 32;

    private final AppConfig config;

    public StartupValidator(AppConfig config) {
        this.config = config;
    }

    static List<String> problems(AppConfig config) {
        List<String> problems = new ArrayList<>();
        AppConfig.Admin admin = config.admin();
        if (admin.password().isBlank() || DEFAULT_ADMIN_PASSWORD.equals(admin.password())) {
            problems.add("ADMIN_PASSWORD must be set to a non-default value");
        }
        if (admin.sessionSecret().length() < MIN_SECRET_LENGTH) {
            problems.add("SESSION_SECRET must be at least " + MIN_SECRET_LENGTH + " characters");
        }
        String token = config.demo().agentToken();
        if (token.length() < MIN_SECRET_LENGTH || DEFAULT_DEMO_TOKEN.equals(token)) {
            problems.add("DEMO_AGENT_TOKEN must be a non-default value of at least " + MIN_SECRET_LENGTH + " characters");
        }
        if (config.paypal().isLive() && (config.paypal().clientId().isBlank() || config.paypal().clientSecret().isBlank())) {
            problems.add("PAYPAL_CLIENT_ID and PAYPAL_CLIENT_SECRET are required");
        }
        if (config.paypal().baseUrl().contains("api-m.paypal.com")) {
            problems.add("PAYPAL_BASE_URL must point at the sandbox, never production");
        }
        return problems;
    }

    @Override
    public void afterPropertiesSet() {
        List<String> problems = problems(config);
        if (problems.isEmpty()) {
            log.info("Configuration OK (paypal mode={}, llm={})", config.paypal().mode(), config.llm().isEnabled() ? config.llm().provider() : "stub");
            return;
        }
        if (config.paypal().isLive()) {
            throw new IllegalStateException("Unsafe configuration for PAYPAL_MODE=live: " + String.join("; ", problems));
        }
        problems.forEach(p -> log.warn("Configuration (tolerated in mock mode): {}", p));
    }
}
