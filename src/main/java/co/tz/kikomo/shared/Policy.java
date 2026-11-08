package co.tz.kikomo.shared;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Policy document (SPEC section 6.1). Field-level limits are declared here; cross-field rules and the
 * configurable HARD_MAX_TX_MINOR ceiling are enforced by {@link PolicyValidator}.
 */
public record Policy(
        int version,
        String currency,
        @Min(1) long perTxCapMinor,
        long dailyBudgetMinor,
        @Min(1) long approvalThresholdMinor,
        @Size(max = 50) List<String> payeeAllowlist,
        boolean newPayeeRequiresApproval,
        List<String> allowedCategories,
        @Valid Velocity velocity,
        boolean refundsRequireApproval,
        @Min(60) @Max(86400) int approvalTtlSeconds) {

    public static Policy demo(List<String> allowlist) {
        return new Policy(1, Money.DEFAULT_CURRENCY, 50000, 60000, 2500, allowlist, true,
                List.of("developer_tools", "events"), new Velocity(5, 60), true, 900);
    }

    public String effectiveCurrency() {
        return currency == null || currency.isBlank() ? Money.DEFAULT_CURRENCY : currency;
    }

    public record Velocity(@Min(1) int maxCalls, @Min(1) int windowSeconds) {
    }
}
