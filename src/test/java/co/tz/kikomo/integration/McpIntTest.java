package co.tz.kikomo.integration;
import co.tz.kikomo.shared.Tools; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class McpIntTest { @Test void eightTools(){ assertEquals(8, Tools.ALL.size()); assertTrue(Tools.ALL.contains("kikomo_fetch_url")); } }
