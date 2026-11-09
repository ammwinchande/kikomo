package co.tz.kikomo.web.dto;

import co.tz.kikomo.shared.Policy;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

/** {@code compiler} is "llm" or "stub" (offline draft / fallback). */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CompileResponse(Policy draft, String sourceText, String compiler) {
}
