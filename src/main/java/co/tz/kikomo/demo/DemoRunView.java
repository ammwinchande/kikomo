package co.tz.kikomo.demo;

import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

/** {@code status} is {@code running}, {@code done} or {@code failed}. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DemoRunView(String id, String scenario, String status, String startedAt, List<DemoStep> steps) {
}
