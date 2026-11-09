package co.tz.kikomo.web.dto;

import java.util.List;

public record CompileRequest(String text, List<String> allowlist) {
}
