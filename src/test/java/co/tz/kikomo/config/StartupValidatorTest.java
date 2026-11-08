package co.tz.kikomo.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StartupValidatorTest {
    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    private static AppConfig config(AppConfig.PayPalMode mode, String adminPassword, String sessionSecret, String token, String clientId) {
        return new AppConfig("https://kikomo.example", 100000, List.of("localhost"),
                new AppConfig.PayPal(mode, "https://api-m.sandbox.paypal.com", clientId, clientId),
                new AppConfig.Admin(adminPassword, sessionSecret),
                new AppConfig.Demo(true, token, "a@b.co", "c@d.co", "e@f.co"),
                new AppConfig.Llm("stub", ""),
                new AppConfig.Approval(Duration.ofSeconds(25), Duration.ofMillis(500)));
    }

    @Test
    void safeLiveConfigPasses() {
        assertDoesNotThrow(() -> new StartupValidator(config(AppConfig.PayPalMode.LIVE, "s3cret!", SECRET, SECRET, "id")).afterPropertiesSet());
    }

    @Test
    void liveModeWithDefaultsFailsFast() {
        var validator = new StartupValidator(config(AppConfig.PayPalMode.LIVE, "admin", "", StartupValidator.DEFAULT_DEMO_TOKEN, ""));
        IllegalStateException e = assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
        assertTrue(e.getMessage().contains("ADMIN_PASSWORD"));
        assertTrue(e.getMessage().contains("SESSION_SECRET"));
        assertTrue(e.getMessage().contains("DEMO_AGENT_TOKEN"));
        assertTrue(e.getMessage().contains("PAYPAL_CLIENT_ID"));
    }

    @Test
    void mockModeOnlyWarns() {
        assertDoesNotThrow(() -> new StartupValidator(config(AppConfig.PayPalMode.MOCK, "admin", "", "short", "")).afterPropertiesSet());
    }

    @Test
    void fetchHostsIncludePublicUrlHost() {
        assertEquals(List.of("localhost", "kikomo.example"), config(AppConfig.PayPalMode.MOCK, "x", SECRET, SECRET, "").effectiveFetchHosts());
    }
}
