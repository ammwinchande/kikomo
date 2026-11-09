package co.tz.kikomo.mcp;

import co.tz.kikomo.gateway.GatewayManager;
import co.tz.kikomo.gateway.ToolEnvelope;
import co.tz.kikomo.gateway.ToolRequest;
import co.tz.kikomo.shared.Tools;
import co.tz.kikomo.shared.error.ToolExecutionException;
import io.modelcontextprotocol.common.McpTransportContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The eight Kikomo MCP tools (SPEC sections 1 and 7). Every tool goes through {@link GatewayManager}; this class
 * only maps typed MCP arguments to the gateway's argument map.
 * <p>
 * Parameter names are snake_case on purpose: Spring AI derives the MCP argument names (the public tool
 * contract) from them, and {@code @McpToolParam} has no name attribute.
 */
@Component
@SuppressWarnings("java:S117")
public class KikomoMcpTools {
    private static final Logger log = LoggerFactory.getLogger(KikomoMcpTools.class);

    private static final String AMOUNT = "Amount as a decimal string in major units, e.g. \"12.50\" (max 2 decimals).";
    private static final String CURRENCY = "ISO currency code. Only USD is supported; defaults to USD.";
    private static final String CATEGORY = "Spending category checked against the policy, e.g. developer_tools or events.";
    private static final String PAYEE = "E-mail address of the PayPal account receiving the money.";

    private final GatewayManager gateway;

    public KikomoMcpTools(GatewayManager gateway) {
        this.gateway = gateway;
    }

    /**
     * Builds an argument map from key/value pairs, skipping null values (absent optional arguments).
     */
    private static Map<String, Object> args(Object... keyValues) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            if (keyValues[i + 1] != null) out.put((String) keyValues[i], keyValues[i + 1]);
        }
        return out;
    }

    @McpTool(name = Tools.SEND_PAYMENT, description = "Send money to a PayPal account (Payouts API). Subject to the agent's spending policy; may wait for human approval.")
    public ToolEnvelope sendPayment(McpTransportContext context,
                                    @McpToolParam(description = PAYEE) String payee_email,
                                    @McpToolParam(description = AMOUNT) String amount,
                                    @McpToolParam(description = CURRENCY, required = false) String currency,
                                    @McpToolParam(description = CATEGORY, required = false) String category,
                                    @McpToolParam(description = "Note shown to the recipient.", required = false) String note) {
        return call(context, Tools.SEND_PAYMENT, args(ToolRequest.ARG_PAYEE_EMAIL, payee_email, ToolRequest.ARG_AMOUNT, amount,
                ToolRequest.ARG_CURRENCY, currency, ToolRequest.ARG_CATEGORY, category, ToolRequest.ARG_NOTE, note));
    }

    @McpTool(name = Tools.CREATE_ORDER, description = "Create a PayPal checkout order payable to the given payee.")
    public ToolEnvelope createOrder(McpTransportContext context,
                                    @McpToolParam(description = PAYEE) String payee_email,
                                    @McpToolParam(description = AMOUNT) String amount,
                                    @McpToolParam(description = CURRENCY, required = false) String currency,
                                    @McpToolParam(description = CATEGORY, required = false) String category) {
        return call(context, Tools.CREATE_ORDER, args(ToolRequest.ARG_PAYEE_EMAIL, payee_email, ToolRequest.ARG_AMOUNT, amount,
                ToolRequest.ARG_CURRENCY, currency, ToolRequest.ARG_CATEGORY, category));
    }

    @McpTool(name = Tools.GET_ORDER, annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true), description = "Read a PayPal checkout order. Read-only.")
    public ToolEnvelope getOrder(McpTransportContext context,
                                 @McpToolParam(description = "PayPal order id.") String order_id) {
        return call(context, Tools.GET_ORDER, args(ToolRequest.ARG_ORDER_ID, order_id));
    }

    @McpTool(name = Tools.CREATE_INVOICE, description = "Create a PayPal invoice addressed to the given recipient.")
    public ToolEnvelope createInvoice(McpTransportContext context,
                                      @McpToolParam(description = "E-mail address of the invoice recipient.") String payee_email,
                                      @McpToolParam(description = AMOUNT) String amount,
                                      @McpToolParam(description = CURRENCY, required = false) String currency,
                                      @McpToolParam(description = CATEGORY, required = false) String category) {
        return call(context, Tools.CREATE_INVOICE, args(ToolRequest.ARG_PAYEE_EMAIL, payee_email, ToolRequest.ARG_AMOUNT, amount,
                ToolRequest.ARG_CURRENCY, currency, ToolRequest.ARG_CATEGORY, category));
    }

    @McpTool(name = Tools.CREATE_REFUND, description = "Refund a captured PayPal payment. Refunds may require human approval.")
    public ToolEnvelope createRefund(McpTransportContext context,
                                     @McpToolParam(description = "PayPal capture id to refund.") String capture_id,
                                     @McpToolParam(description = "E-mail address of the customer being refunded.") String payee_email,
                                     @McpToolParam(description = AMOUNT) String amount,
                                     @McpToolParam(description = CURRENCY, required = false) String currency,
                                     @McpToolParam(description = CATEGORY, required = false) String category) {
        return call(context, Tools.CREATE_REFUND, args(ToolRequest.ARG_CAPTURE_ID, capture_id, ToolRequest.ARG_PAYEE_EMAIL, payee_email,
                ToolRequest.ARG_AMOUNT, amount, ToolRequest.ARG_CURRENCY, currency, ToolRequest.ARG_CATEGORY, category));
    }

    @McpTool(name = Tools.LIST_TRANSACTIONS, annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true), description = "List PayPal transactions of the last 30 days. Read-only.")
    public ToolEnvelope listTransactions(McpTransportContext context) {
        return call(context, Tools.LIST_TRANSACTIONS, Map.of());
    }

    @McpTool(name = Tools.CHECK_APPROVAL, annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false, idempotentHint = true), description = "Check a pending approval (by approval_id) or a call (by call_id). Use after a call returned pending_approval.")
    public ToolEnvelope checkApproval(McpTransportContext context,
                                      @McpToolParam(description = "Approval id returned with status pending_approval.", required = false) String approval_id,
                                      @McpToolParam(description = "Call id, if no approval id is known.", required = false) String call_id) {
        return call(context, Tools.CHECK_APPROVAL, args(ToolRequest.ARG_APPROVAL_ID, approval_id, ToolRequest.ARG_CALL_ID, call_id));
    }

    @McpTool(name = Tools.FETCH_URL, description = "Fetch a web page from an allow-listed host. E-mail addresses found in it are marked untrusted and cannot be paid.")
    public ToolEnvelope fetchUrl(McpTransportContext context,
                                 @McpToolParam(description = "http(s) URL on an allowed host.") String url) {
        return call(context, Tools.FETCH_URL, args(ToolRequest.ARG_URL, url));
    }

    private ToolEnvelope call(McpTransportContext context, String tool, Map<String, Object> args) {
        UUID agentId = (UUID) context.get(McpServerConfig.AGENT_ID_KEY);
        if (agentId == null) {
            throw new ToolExecutionException("unauthorized"); // unreachable: AgentTokenFilter rejects first
        }
        try {
            return gateway.execute(agentId, tool, args);
        } catch (ToolExecutionException e) {
            throw e;
        } catch (RuntimeException e) {
            String reference = UUID.randomUUID().toString();
            log.error("Tool {} failed for agent {} (reference {})", tool, agentId, reference, e);
            throw new ToolExecutionException("Internal error, reference " + reference, e);
        }
    }
}
