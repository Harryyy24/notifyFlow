package com.notifyflow.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an operation is attempted without valid authentication,
 * or when the authenticated user lacks permission for the operation.
 *
 * Results in HTTP 401 Unauthorized.
 *
 * Note: Spring Security throws its own AccessDeniedException (403)
 * for @PreAuthorize failures. This exception is for application-level
 * authorization checks (e.g. a user trying to access another user's data).
 */
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}