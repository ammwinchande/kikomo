package co.tz.kikomo.shared.error;

/**
 * A tool could not be executed (bad tool arguments, PayPal error, fetch error). The message is safe to
 * return to the calling agent; it must not contain secrets or stack details.
 */
public class ToolExecutionException extends RuntimeException {
    public ToolExecutionException(String message) {
        super(message);
    }

    public ToolExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
