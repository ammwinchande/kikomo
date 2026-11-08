package co.tz.kikomo.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Single source of truth for application settings (SPEC section 4). Environment variables are mapped onto
 * {@code kikomo.*} in {@code application.yml}; secrets required in live mode are checked by
 * {@link StartupValidator}.
 */
@Validated
@ConfigurationProperties(prefix = "kikomo")
public record AppConfig(
        @NotBlank @DefaultValue("http://localhost:8080") String publicUrl,
        @Min(1) @DefaultValue("100000") long hardMaxTxMinor,
        @NotNull @DefaultValue({"localhost", "127.0.0.1"}) List<String> fetchAllowedHosts,
        @Valid @NotNull @DefaultValue PayPal paypal,
        @Valid @NotNull @DefaultValue Admin admin,
        @Valid @NotNull @DefaultValue Demo demo,
        @Valid @NotNull @DefaultValue Llm llm,
        @Valid @NotNull @DefaultValue Approval approval) {

    public enum PayPalMode { MOCK, LIVE }

    public record PayPal(
            @NotNull @DefaultValue("mock") PayPalMode mode,
            @NotBlank @DefaultValue("https://api-m.sandbox.paypal.com") String baseUrl,
            @DefaultValue("") String clientId,
            @DefaultValue("") String clientSecret) {
        public boolean isLive() {
            return mode == PayPalMode.LIVE;
        }
    }

    public record Admin(
            @DefaultValue("admin") String password,
            @DefaultValue("") String sessionSecret) {
    }

    public record Demo(
            @DefaultValue("true") boolean mode,
            @DefaultValue("demo-token-change-me-32chars!!") String agentToken,
            @DefaultValue("devtools@example.com") String payeeDevtools,
            @DefaultValue("conf@example.com") String payeeConf,
            @DefaultValue("pay-me@evil-payments.example") String payeeAttacker) {
    }

    public record Llm(
            @DefaultValue("stub") String provider,
            @DefaultValue("") String apiKey) {
        /** True when a real model should be called instead of the deterministic offline stub. */
        public boolean isEnabled() {
            return !"stub".equals(provider.toLowerCase(Locale.ROOT)) && !apiKey.isBlank();
        }
    }

    /** SPEC 7: the gateway waits up to 25 seconds for a human decision before returning pending_approval. */
    public record Approval(
            @NotNull @DefaultValue("25s") Duration waitTimeout,
            @NotNull @DefaultValue("500ms") Duration pollInterval) {
    }

    /** Hosts kikomo_fetch_url may contact: FETCH_ALLOWED_HOSTS plus the host of PUBLIC_URL. */
    public List<String> effectiveFetchHosts() {
        String publicHost = URI.create(publicUrl).getHost();
        return Stream.concat(fetchAllowedHosts.stream(), Stream.ofNullable(publicHost))
                .map(h -> h.trim().toLowerCase(Locale.ROOT))
                .filter(h -> !h.isEmpty())
                .distinct()
                .toList();
    }

}
