package co.tz.kikomo.web;

import co.tz.kikomo.shared.error.ConflictException;
import co.tz.kikomo.shared.error.NotFoundException;
import co.tz.kikomo.shared.error.PolicyValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.UUID;

/**
 * Maps errors of the admin API to RFC 9457 problem details. Malformed bodies and @Valid failures are handled by
 * {@link ResponseEntityExceptionHandler} (400). Unexpected errors are logged with a reference id that is returned
 * to the client instead of internal details.
 */
@RestControllerAdvice(basePackages = "co.tz.kikomo")
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    ProblemDetail conflict(ConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(PolicyValidationException.class)
    ProblemDetail invalidPolicy(PolicyValidationException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "invalid policy");
        problem.setProperty("errors", e.errors());
        return problem;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e, HttpServletRequest request) {
        String reference = UUID.randomUUID().toString();
        log.error("Unhandled error on {} {} (reference {})", request.getMethod(), request.getRequestURI(), reference, e);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "internal error");
        problem.setProperty("reference", reference);
        return problem;
    }
}
