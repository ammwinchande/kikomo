package co.tz.kikomo.web;

import co.tz.kikomo.config.AppConfig;
import co.tz.kikomo.paypal.PayPalClient;
import co.tz.kikomo.web.dto.HealthView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Locale;

@RestController
@RequestMapping("/api")
public class HealthController {
    private final PayPalClient paypal;
    private final AppConfig config;

    public HealthController(PayPalClient paypal, AppConfig config) {
        this.paypal = paypal;
        this.config = config;
    }

    @GetMapping("/health")
    public HealthView health() {
        return new HealthView("ok", config.paypal().mode().name().toLowerCase(Locale.ROOT), paypal.isMock());
    }
}
