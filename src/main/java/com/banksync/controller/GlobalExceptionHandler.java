package com.banksync.controller;
import com.banksync.exception.*;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    public record ApiError(Instant timestamp, int status, String code, String message, String path,
                           Map<String, String> fieldErrors) {}
    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiError> notFound(AccountNotFoundException e, HttpServletRequest r) {
        return error(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", e.getMessage(), r, Map.of());
    }
    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiError> insufficient(InsufficientFundsException e, HttpServletRequest r) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", e.getMessage(), r, Map.of());
    }
    @ExceptionHandler({ConcurrentTransferException.class, ObjectOptimisticLockingFailureException.class,
            OptimisticLockException.class, ConcurrencyFailureException.class})
    public ResponseEntity<ApiError> conflict(Exception e, HttpServletRequest r) {
        return error(HttpStatus.CONFLICT, "CONCURRENT_TRANSFER", "Account changed or is busy; refresh balances before retrying", r, Map.of());
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e, HttpServletRequest r) {
        Map<String, String> fields = new TreeMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", r, fields);
    }
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> invalid(IllegalArgumentException e, HttpServletRequest r) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_TRANSFER", e.getMessage(), r, Map.of());
    }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> invalidQuery(MethodArgumentTypeMismatchException e, HttpServletRequest r) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_QUERY", "Query parameters have an invalid format", r, Map.of());
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> malformed(HttpMessageNotReadableException e, HttpServletRequest r) {
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_JSON", "Request must contain a valid JSON transfer", r, Map.of());
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e, HttpServletRequest r) {
        log.error("Unhandled request failure at {}", r.getRequestURI(), e);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred", r, Map.of());
    }
    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message,
                                           HttpServletRequest request, Map<String, String> fields) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), code,
                message, request.getRequestURI(), fields));
    }
}
