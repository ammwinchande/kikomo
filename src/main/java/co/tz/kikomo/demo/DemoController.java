package co.tz.kikomo.demo;

import co.tz.kikomo.config.AppConfig;
import co.tz.kikomo.shared.error.NotFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Demo runner API (SPEC section 14). Every endpoint answers 404 unless {@code kikomo.demo.mode} is on, so a hosted
 * instance can switch the scripted agent off without touching the SPA.
 */
@RestController
@RequestMapping("/api/demo")
public class DemoController {
    private final DemoRunService runs;
    private final AppConfig config;

    public DemoController(DemoRunService runs, AppConfig config) {
        this.runs = runs;
        this.config = config;
    }

    @PostMapping("/runs")
    public DemoRunView start(@RequestBody StartRequest body) {
        requireDemoMode();
        return runs.start(DemoScenario.fromValue(body == null ? null : body.scenario()));
    }

    @GetMapping("/runs/{id}")
    public DemoRunView get(@PathVariable("id") UUID id) {
        requireDemoMode();
        return runs.find(id).orElseThrow(() -> new NotFoundException("demo run " + id + " not found"));
    }

    /**
     * What an external agent needs to connect. The token itself is never returned.
     */
    @GetMapping("/connect")
    public Map<String, String> connect() {
        requireDemoMode();
        return Map.of("mcp_url", config.publicUrl().replaceAll("/+$", "") + "/mcp",
                "malicious_page_url", config.publicUrl().replaceAll("/+$", "") + "/demo/malicious-page");
    }

    private void requireDemoMode() {
        if (!config.demo().mode()) throw new NotFoundException("demo mode is off");
    }

    public record StartRequest(String scenario) {
    }
}
