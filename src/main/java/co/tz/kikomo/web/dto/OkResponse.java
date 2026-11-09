package co.tz.kikomo.web.dto;

public record OkResponse(boolean ok) {
    public static final OkResponse OK_RESPONSE = new OkResponse(true);
}
