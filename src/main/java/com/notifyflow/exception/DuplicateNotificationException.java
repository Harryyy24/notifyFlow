package com.notifyflow.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a notification request is detected as a duplicate
 * within the Redis deduplication TTL window (default 10 minutes).
 *
 * Results in HTTP 409 Conflict.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateNotificationException extends RuntimeException {

    public DuplicateNotificationException(String message) {
        super(message);
    }

    public DuplicateNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}