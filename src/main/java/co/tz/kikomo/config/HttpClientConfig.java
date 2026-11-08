package co.tz.kikomo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/** One shared, thread-safe {@link HttpClient} for outbound calls (PayPal, kikomo_fetch_url). */
@Configuration
public class HttpClientConfig {
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Redirects are never followed: a redirect from an allowed host could otherwise point
     * kikomo_fetch_url at an internal address (SSRF).
     */
    @Bean(destroyMethod = "close")
    HttpClient outboundHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}
