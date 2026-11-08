package co.tz.kikomo.shared;

import co.tz.kikomo.config.AppConfig;
import co.tz.kikomo.shared.error.PolicyValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Validates a policy document: bean-validation annotations plus SPEC 6.1 cross-field rules.
 */
@Component
public class PolicyValidator {

    private final Validator validator;
    private final long hardMaxTxMinor;

    public PolicyValidator(Validator validator, AppConfig config) {
        this.validator = validator;
        this.hardMaxTxMinor = config.hardMaxTxMinor();
    }

    private static String describe(ConstraintViolation<Policy> v) {
        return v.getPropertyPath() + " " + v.getMessage();
    }

    /**
     * @throws PolicyValidationException listing every violation
     */
    public void validate(Policy policy) {
        List<String> errors = new ArrayList<>();
        if (policy == null) {
            throw new PolicyValidationException(List.of("policy is required"));
        }
        validator.validate(policy).stream()
                .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                .map(PolicyValidator::describe)
                .forEach(errors::add);
        if (policy.perTxCapMinor() > hardMaxTxMinor) {
            errors.add("perTxCapMinor must be <= HARD_MAX_TX_MINOR (" + hardMaxTxMinor + ")");
        }
        if (policy.dailyBudgetMinor() < policy.perTxCapMinor()) {
            errors.add("dailyBudgetMinor must be >= perTxCapMinor");
        }
        if (policy.approvalThresholdMinor() > policy.perTxCapMinor()) {
            errors.add("approvalThresholdMinor must be <= perTxCapMinor");
        }
        if (!Money.DEFAULT_CURRENCY.equals(policy.effectiveCurrency())) {
            errors.add("currency must be USD");
        }
        if (!errors.isEmpty()) {
            throw new PolicyValidationException(errors);
        }
    }
}
