package com.notifyflow.exception;

import com.notifyflow.dto.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Centralised exception handler for all controllers.
 *
 * Every exception type is caught here and mapped to a consistent
 * ErrorResponseDTO structure. This means:
 *
 *   - Controllers stay clean (no try/catch blocks)
 *   - Error response format is uniform across all endpoints
 *   - New exception types are handled in one place
 *
 * Handler priority: most specific exception first,
 * generic Exception catch-all last.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ── Application Exceptions ─────────────────────────────────────

    /**
     * 404 — Resource not found (user, notification, preferences)
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleResourceNotFound(
            ResourceNotFoundException ex,
            HttpServletRequest request) {

        log.warn("Resource not found — path=[{}] message=[{}]",
                request.getRequestURI(), ex.getMessage());

        return buildResponse(
                HttpStatus.NOT_FOUND,
                "Not Found",
                ex.getMessage(),
                request.getRequestURI());
    }

    /**
     * 409 — Duplicate notification within deduplication window
     */
    @ExceptionHandler(DuplicateNotificationException.class)
    public ResponseEntity<ErrorResponseDTO> handleDuplicateNotification(
            DuplicateNotificationException ex,
            HttpServletRequest request) {

        log.warn("Duplicate notification rejected — path=[{}] message=[{}]",
                request.getRequestURI(), ex.getMessage());

        return buildResponse(
                HttpStatus.CONFLICT,
                "Conflict",
                ex.getMessage(),
                request.getRequestURI());
    }

    /**
     * 429 — Quiet hours active, LOW priority notification suppressed
     */
    @ExceptionHandler(QuietHoursActiveException.class)
    public ResponseEntity<ErrorResponseDTO> handleQuietHours(
            QuietHoursActiveException ex,
            HttpServletRequest request) {

        log.info("Quiet hours active — suppressing notification — " +
                "path=[{}]", request.getRequestURI());

        return buildResponse(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                ex.getMessage(),
                request.getRequestURI());
    }

    /**
     * 401 — Application-level unauthorized access
     */
    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponseDTO> handleUnauthorized(
            UnauthorizedException ex,
            HttpServletRequest request) {

        log.warn("Unauthorized access — path=[{}] message=[{}]",
                request.getRequestURI(), ex.getMessage());

        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                ex.getMessage(),
                request.getRequestURI());
    }

    // ── Spring Security Exceptions ─────────────────────────────────

    /**
     * 401 — Bad credentials on login attempt
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponseDTO> handleBadCredentials(
            BadCredentialsException ex,
            HttpServletRequest request) {

        log.warn("Bad credentials — path=[{}]", request.getRequestURI());

        // Intentionally vague message — don't reveal whether
        // the email exists or the password is wrong
        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                "Invalid email or password",
                request.getRequestURI());
    }

    /**
     * 401 — Account disabled
     */
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ErrorResponseDTO> handleDisabled(
            DisabledException ex,
            HttpServletRequest request) {

        log.warn("Disabled account login attempt — path=[{}]",
                request.getRequestURI());

        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                "Account is disabled. Please contact support.",
                request.getRequestURI());
    }

    /**
     * 401 — Account locked
     */
    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ErrorResponseDTO> handleLocked(
            LockedException ex,
            HttpServletRequest request) {

        log.warn("Locked account login attempt — path=[{}]",
                request.getRequestURI());

        return buildResponse(
                HttpStatus.UNAUTHORIZED,
                "Unauthorized",
                "Account is locked. Please contact support.",
                request.getRequestURI());
    }

    /**
     * 403 — Authenticated but insufficient role (@PreAuthorize failure)
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDTO> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request) {

        log.warn("Access denied — path=[{}] message=[{}]",
                request.getRequestURI(), ex.getMessage());

        return buildResponse(
                HttpStatus.FORBIDDEN,
                "Forbidden",
                "You do not have permission to perform this action. " +
                        "ADMIN role is required.",
                request.getRequestURI());
    }

    // ── Validation Exceptions ──────────────────────────────────────

    /**
     * 400 — @Valid bean validation failures
     *
     * Collects all field errors into a single readable message:
     * "field1: error message; field2: error message"
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        String errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));

        log.warn("Validation failed — path=[{}] errors=[{}]",
                request.getRequestURI(), errors);

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                errors,
                request.getRequestURI());
    }

    /**
     * 400 — Malformed JSON request body
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponseDTO> handleMalformedJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {

        log.warn("Malformed request body — path=[{}]",
                request.getRequestURI());

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                "Malformed or unreadable request body. " +
                        "Please check your JSON format and field types.",
                request.getRequestURI());
    }

    /**
     * 400 — Missing required request parameter
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponseDTO> handleMissingParam(
            MissingServletRequestParameterException ex,
            HttpServletRequest request) {

        log.warn("Missing request parameter — path=[{}] param=[{}]",
                request.getRequestURI(), ex.getParameterName());

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                "Required parameter '" + ex.getParameterName() +
                        "' of type " + ex.getParameterType() + " is missing.",
                request.getRequestURI());
    }

    /**
     * 400 — Path variable or request param type mismatch
     * (e.g. /notifications/abc when Long expected)
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {

        String expectedType = ex.getRequiredType() != null
                ? ex.getRequiredType().getSimpleName()
                : "unknown";

        log.warn("Type mismatch — path=[{}] param=[{}] expected=[{}]",
                request.getRequestURI(), ex.getName(), expectedType);

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                String.format("Parameter '%s' must be of type %s. " +
                                "Received: '%s'",
                        ex.getName(), expectedType, ex.getValue()),
                request.getRequestURI());
    }

    /**
     * 400 — IllegalArgumentException
     * (e.g. email already registered during registration)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDTO> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        log.warn("Illegal argument — path=[{}] message=[{}]",
                request.getRequestURI(), ex.getMessage());

        return buildResponse(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                ex.getMessage(),
                request.getRequestURI());
    }

    // ── Infrastructure Exceptions ──────────────────────────────────

    /**
     * 503 — Redis connection failure (deduplication unavailable).
     * Allows notifications to proceed without dedup check
     * rather than blocking all sends on a Redis outage.
     */
    @ExceptionHandler(RedisConnectionFailureException.class)
    public ResponseEntity<ErrorResponseDTO> handleRedisConnectionFailure(
            RedisConnectionFailureException ex,
            HttpServletRequest request) {

        log.error("Redis connection failed — path=[{}] error=[{}]",
                request.getRequestURI(), ex.getMessage(), ex);

        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Service Unavailable",
                "The deduplication service is temporarily unavailable. " +
                        "Please try again later.",
                request.getRequestURI());
    }

    /**
     * 503 — Database connection failure.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorResponseDTO> handleDataAccess(
            DataAccessException ex,
            HttpServletRequest request) {

        log.error("Database access error — path=[{}] error=[{}]",
                request.getRequestURI(), ex.getMessage(), ex);

        return buildResponse(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Service Unavailable",
                "The database service is temporarily unavailable. " +
                        "Please try again later.",
                request.getRequestURI());
    }

    // ── Catch-All ──────────────────────────────────────────────────

    /**
     * 500 — Any unhandled exception.
     *
     * Logs the full stack trace internally but returns a generic
     * message externally — never expose internal error details
     * (stack traces, SQL, class names) to API consumers.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGenericException(
            Exception ex,
            HttpServletRequest request) {

        log.error("Unhandled exception — path=[{}] error=[{}]",
                request.getRequestURI(), ex.getMessage(), ex);

        return buildResponse(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected error occurred. " +
                        "Please try again later or contact support.",
                request.getRequestURI());
    }

    // ── Builder ────────────────────────────────────────────────────

    /**
     * Constructs a standardised ErrorResponseDTO wrapped in a ResponseEntity.
     *
     * @param status  HTTP status
     * @param error   HTTP reason phrase
     * @param message human-readable detail
     * @param path    request URI for debugging
     * @return ResponseEntity with ErrorResponseDTO body
     */
    private ResponseEntity<ErrorResponseDTO> buildResponse(
            HttpStatus status,
            String error,
            String message,
            String path) {

        ErrorResponseDTO body = ErrorResponseDTO.builder()
                .status(status.value())
                .error(error)
                .message(message)
                .timestamp(LocalDateTime.now())
                .path(path)
                .build();

        return ResponseEntity.status(status).body(body);
    }
}