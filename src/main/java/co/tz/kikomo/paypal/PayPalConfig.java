package co.tz.kikomo.paypal;

import co.tz.kikomo.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Clock;

/** Chooses the live sandbox client or the offline mock from {@code PAYPAL_MODE}. */
@Configuration
public class PayPalConfig {
    private static final Logger log = LoggerFactory.getLogger(PayPalConfig.class);

    @Bean
    PayPalClient payPalClient(AppConfig config, HttpClient outboundHttpClient, JsonMapper json) {
        AppConfig.PayPal paypal = config.paypal();
        if (paypal.isLive()) {
            log.info("PayPal: LIVE sandbox client at {}", paypal.baseUrl());
            return new LivePayPalClient(paypal.baseUrl(), paypal.clientId(), paypal.clientSecret(), outboundHttpClient, json, Clock.systemUTC());
        }
        log.warn("PayPal: MOCK mode, no real money moves");
        return new MockPayPalClient();
    }
}
