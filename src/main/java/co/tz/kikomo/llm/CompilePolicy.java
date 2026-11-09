package co.tz.kikomo.llm;

import co.tz.kikomo.config.AppConfig;
import co.tz.kikomo.shared.Policy;
import co.tz.kikomo.shared.PolicyValidator;
import co.tz.kikomo.shared.error.PolicyValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Natural-language policy compiler (SPEC section 12). Produces a DRAFT only: a model never enforces anything,
 * and every draft passes {@link PolicyValidator}. Without a configured model (LLM_PROVIDER=stub or no key) or on
 * any model failure, a deterministic offline draft is returned instead.
 */
@Service
public class CompilePolicy {
    private static final Logger log = LoggerFactory.getLogger(CompilePolicy.class);
    private static final long STUB_PER_TX_CAP_MINOR = 50_000;
    private static final long STUB_DAILY_BUDGET_MINOR = 60_000;
    private static final long STUB_APPROVAL_THRESHOLD_MINOR = 2_500;
    private static final int STUB_APPROVAL_TTL_SECONDS = 900;
    private static final List<String> STUB_CATEGORIES = List.of("developer_tools", "events");

    private final ObjectProvider<ChatClient.Builder> chatClientBuilder;
    private final PolicyValidator validator;
    private final AppConfig config;

    public CompilePolicy(ObjectProvider<ChatClient.Builder> chatClientBuilder, PolicyValidator validator, AppConfig config) {
        this.chatClientBuilder = chatClientBuilder;
        this.validator = validator;
        this.config = config;
    }

    /**
     * Deterministic offline draft, used for the demo and as the fallback.
     */
    static Policy stub(List<String> allowlist) {
        return new Policy(1, "USD", STUB_PER_TX_CAP_MINOR, STUB_DAILY_BUDGET_MINOR, STUB_APPROVAL_THRESHOLD_MINOR, allowlist, true,
                STUB_CATEGORIES, new Policy.Velocity(5, 60), true, STUB_APPROVAL_TTL_SECONDS);
    }

    public CompiledPolicy compile(String text, List<String> defaultAllowlist) {
        List<String> allowlist = defaultAllowlist == null ? List.of() : defaultAllowlist;
        if (!config.llm().isEnabled()) {
            return new CompiledPolicy(stub(allowlist), Source.STUB);
        }
        try {
            Policy draft = compileWithModel(text, allowlist);
            validator.validate(draft);
            return new CompiledPolicy(draft, Source.LLM);
        } catch (PolicyValidationException e) {
            log.warn("LLM draft rejected by validation ({}); returning offline draft", e.errors());
        } catch (RuntimeException e) {
            log.warn("LLM policy compilation failed ({}: {}); returning offline draft", e.getClass().getSimpleName(), e.getMessage());
        }
        return new CompiledPolicy(stub(allowlist), Source.STUB);
    }

    private Policy compileWithModel(String text, List<String> allowlist) {
        ChatClient chat = chatClientBuilder.getObject().build();
        Policy draft = chat.prompt()
                .system("""
                        You convert a spending policy written in English into a Kikomo policy document.
                        Amounts are integer minor units (cents). Currency is always USD.
                        perTxCapMinor must be between 1 and %d. dailyBudgetMinor >= perTxCapMinor.
                        1 <= approvalThresholdMinor <= perTxCapMinor. approvalTtlSeconds between 60 and 86400.
                        If the text does not say otherwise use payeeAllowlist %s, newPayeeRequiresApproval true,
                        refundsRequireApproval true, velocity 5 calls per 60 seconds, approvalTtlSeconds 900, version 1.
                        """.formatted(config.hardMaxTxMinor(), allowlist))
                .user(text == null ? "" : text)
                .call()
                .entity(Policy.class);
        if (draft == null) {
            throw new IllegalStateException("model returned no policy");
        }
        return draft;
    }

    public enum Source {LLM, STUB}

    public record CompiledPolicy(Policy draft, Source source) {
    }
}
