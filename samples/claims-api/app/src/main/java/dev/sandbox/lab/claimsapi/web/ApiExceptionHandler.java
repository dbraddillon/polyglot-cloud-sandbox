package dev.sandbox.lab.claimsapi.web;

import dev.sandbox.lab.claimsapi.domain.InvalidClaimStateException;
import dev.sandbox.lab.claimsapi.service.ClaimNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ClaimNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ClaimNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(InvalidClaimStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidState(InvalidClaimStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }
}
