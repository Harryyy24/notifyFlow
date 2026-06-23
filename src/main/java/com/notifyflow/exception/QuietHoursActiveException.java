package com.notifyflow.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a LOW priority notification is submitted during
 * the user's configured quiet hours window.
 *
 * Results in HTTP 429 Too Many Requests.
 * (429 is used here as "request suppressed by rate/preference policy"
 * rather than strict rate limiting — a common convention.)
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class QuietHoursActiveException extends RuntimeException {

    public QuietHoursActiveException(String message) {
        super(message);
    }

    public QuietHoursActiveException(String message, Throwable cause) {
        super(message, cause);
    }
}