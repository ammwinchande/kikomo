package co.tz.kikomo.integration;

import co.tz.kikomo.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * With {@code DEMO_MODE=false} the demo runner does not exist as far as HTTP is concerned.
 */
@TestPropertySource(properties = "kikomo.demo.mode=false")
class DemoModeOffIntTest extends IntegrationTestBase {

    @Test
    void demoEndpointsAreNotFound() throws Exception {
        Admin admin = new Admin().login();
        assertEquals(404, admin.post("/api/demo/runs", "{\"scenario\":\"small\"}").statusCode());
        assertEquals(404, admin.get("/api/demo/connect").statusCode());
        assertEquals(200, admin.get("/demo/malicious-page").statusCode(), "the static demo page is unaffected");
    }
}
