package dev.sandbox.lab.claimsintakeapi.web;

import dev.sandbox.lab.claimsintakeapi.domain.BatchNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BatchNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(BatchNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
    }

    // Spring throws this itself once an upload exceeds spring.servlet.multipart.max-file-size/
    // max-request-size (see application.yml) - without this handler it falls through to the
    // catch-all below and comes back as a 500, which is misleading for what's actually a bad
    // request from the caller, not a server failure.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("Uploaded file is too large"));
    }

    // A catch-all safety net: anything not covered above - an AWS SDK exception from Kinesis, a
    // stray IOException, an ArithmeticException like the one this sample used to let through
    // from unbounded BigDecimal parsing (see commit cf07b52) - previously fell through to Spring
    // Boot's default /error handling. That's not unsafe by default (include-message/
    // include-stacktrace default to "never"), but it's an ambient framework default this code
    // doesn't control, not something it enforces itself. Full detail goes to the log; callers
    // get a fixed, generic message either way.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception processing a claims-intake request", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse("Unexpected error processing the request"));
    }
}
