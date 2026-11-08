package co.tz.kikomo.gateway;

import co.tz.kikomo.shared.Money;

import java.util.Map;

/**
 * Raw MCP tool arguments with typed accessors. Values are kept as the agent sent them.
 */
public record ToolRequest(String tool, Map<String, Object> args) {
    public static final String ARG_AMOUNT = "amount";
    public static final String ARG_CURRENCY = "currency";
    public static final String ARG_PAYEE_EMAIL = "payee_email";
    /**
     * Legacy alias for {@link #ARG_PAYEE_EMAIL}.
     */
    public static final String ARG_COUNTERPARTY = "counterparty";
    public static final String ARG_CATEGORY = "category";
    public static final String ARG_NOTE = "note";
    public static final String ARG_ORDER_ID = "order_id";
    public static final String ARG_CAPTURE_ID = "capture_id";
    public static final String ARG_APPROVAL_ID = "approval_id";
    public static final String ARG_CALL_ID = "call_id";
    public static final String ARG_URL = "url";
    
    public ToolRequest {
        args = args == null ? Map.of() : args;
    }

    public String string(String key) {
        Object value = args.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public String amountRaw() {
        return string(ARG_AMOUNT);
    }

    public String currency() {
        String currency = string(ARG_CURRENCY);
        return currency == null ? Money.DEFAULT_CURRENCY : currency;
    }

    public String counterpartyRaw() {
        String payee = string(ARG_PAYEE_EMAIL);
        if (payee != null) return payee;
        String counterparty = string(ARG_COUNTERPARTY);
        return counterparty == null ? "" : counterparty;
    }

    public String category() {
        return string(ARG_CATEGORY);
    }

    /**
     * @return minor units, or null when absent or malformed
     */
    public Long amountMinor() {
        String raw = amountRaw();
        return Money.isValidFormat(raw) ? Money.parseMinor(raw) : null;
    }
}
