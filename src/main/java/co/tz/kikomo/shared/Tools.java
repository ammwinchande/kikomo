package co.tz.kikomo.shared;

import java.util.Set;

/**
 * The eight MCP tool names (SPEC section 1) and their classifications.
 */
public final class Tools {

    public static final String SEND_PAYMENT = "send_payment";
    public static final String CREATE_ORDER = "create_order";
    public static final String GET_ORDER = "get_order";
    public static final String CREATE_INVOICE = "create_invoice";
    public static final String CREATE_REFUND = "create_refund";
    public static final String LIST_TRANSACTIONS = "list_transactions";
    public static final String CHECK_APPROVAL = "check_approval";
    public static final String FETCH_URL = "kikomo_fetch_url";

    public static final Set<String> READ_ONLY = Set.of(GET_ORDER, LIST_TRANSACTIONS, CHECK_APPROVAL);
    /**
     * Tools whose amount counts against the daily budget (spend ledger).
     */
    public static final Set<String> SPENDING = Set.of(SEND_PAYMENT, CREATE_ORDER, CREATE_INVOICE);
    public static final Set<String> ALL = Set.of(SEND_PAYMENT, CREATE_ORDER, GET_ORDER, CREATE_INVOICE, CREATE_REFUND,
            LIST_TRANSACTIONS, CHECK_APPROVAL, FETCH_URL);

    private Tools() {
        /* utility class */
    }

    public static boolean isReadOnly(String tool) {
        return READ_ONLY.contains(tool);
    }

    public static boolean isRefund(String tool) {
        return CREATE_REFUND.equals(tool);
    }

    public static boolean isFetch(String tool) {
        return FETCH_URL.equals(tool);
    }

    public static boolean isSpending(String tool) {
        return SPENDING.contains(tool);
    }
}
