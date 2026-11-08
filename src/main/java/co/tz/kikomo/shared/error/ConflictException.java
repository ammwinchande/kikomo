package co.tz.kikomo.shared.error;

/** The request conflicts with the current state, e.g. deciding an approval twice (HTTP 409). */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
