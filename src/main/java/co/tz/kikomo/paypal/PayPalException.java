package co.tz.kikomo.paypal;

import co.tz.kikomo.shared.error.ToolExecutionException;

/** PayPal returned a non-2xx response or could not be reached. */
public class PayPalException extends ToolExecutionException {
    private final int httpStatus;

    public PayPalException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public PayPalException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = 0;
    }

    /** 0 when no HTTP response was received. */
    public int httpStatus() {
        return httpStatus;
    }
}
