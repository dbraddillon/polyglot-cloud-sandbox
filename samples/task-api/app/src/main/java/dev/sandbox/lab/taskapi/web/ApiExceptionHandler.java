package dev.sandbox.lab.taskapi.web;

import dev.sandbox.lab.taskapi.domain.InvalidStatusTransitionException;
import dev.sandbox.lab.taskapi.domain.TaskNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// @RestControllerAdvice is one centralized place to turn exceptions thrown anywhere in a
// controller into HTTP responses - similar territory to ASP.NET Core exception-handling
// middleware or an IExceptionFilter, just organized by exception type per method instead of one
// big switch. Bean validation failures (a bad @Valid request body) don't need an entry here -
// Spring Boot already turns those into a 400 automatically.
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TaskNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(TaskNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(InvalidStatusTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }
}
