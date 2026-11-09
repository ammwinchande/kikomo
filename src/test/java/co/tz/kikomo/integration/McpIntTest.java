package co.tz.kikomo.integration;

import co.tz.kikomo.shared.Tools;
import co.tz.kikomo.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** MCP protocol surface: auth, initialize, tools/list. */
class McpIntTest extends IntegrationTestBase {

    private static final String LIST = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}";

    @Test
    void eightTools() {
        assertEquals(8, Tools.ALL.size());
        assertTrue(Tools.ALL.contains("kikomo_fetch_url"));
    }

    @Test
    void missingTokenIsUnauthorized() throws Exception {
        assertEquals(401, mcpRaw(null, LIST).statusCode());
    }

    @Test
    void invalidTokenIsUnauthorized() throws Exception {
        assertEquals(401, mcpRaw("not-a-real-token", LIST).statusCode());
    }

    @Test
    void initializeAdvertisesKikomo() throws Exception {
        HttpResponse<String> r = mcpRaw(newAgent().token(), """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""");
        assertEquals(200, r.statusCode());
        JsonNode result = JSON.readTree(r.body()).path("result");
        assertEquals("kikomo", result.path("serverInfo").path("name").asString());
        assertTrue(result.path("capabilities").has("tools"));
    }

    @Test
    void toolsListExposesAllEightToolsWithSchemas() throws Exception {
        HttpResponse<String> r = mcpRaw(newAgent().token(), LIST);
        assertEquals(200, r.statusCode());
        Set<String> names = new HashSet<>();
        for (JsonNode t : JSON.readTree(r.body()).path("result").path("tools")) {
            names.add(t.path("name").asString());
            assertEquals("object", t.path("inputSchema").path("type").asString());
        }
        assertEquals(Tools.ALL, names);
    }

    @Test
    void sendPaymentSchemaDescribesItsArguments() throws Exception {
        HttpResponse<String> r = mcpRaw(newAgent().token(), LIST);
        JsonNode send = null;
        for (JsonNode t : JSON.readTree(r.body()).path("result").path("tools")) {
            if ("send_payment".equals(t.path("name").asString())) send = t;
        }
        assertNotNull(send);
        JsonNode props = send.path("inputSchema").path("properties");
        assertTrue(props.has("payee_email") && props.has("amount"), props.toString());
    }
}
