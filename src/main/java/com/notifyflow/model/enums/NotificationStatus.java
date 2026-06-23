package com.notifyflow.model.enums;

/**
 * Lifecycle states of a notification.
 *
 * PENDING   → saved to DB, published to Kafka, awaiting consumer processing
 * DELIVERED → consumer successfully simulated delivery
 * FAILED    → consumer exhausted retries or manual override via PATCH endpoint
 */
public enum NotificationStatus {
    PENDING,
    DELIVERED,
    FAILED
}