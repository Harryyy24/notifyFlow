package com.notifyflow.model.enums;

/**
 * Supported notification delivery channels.
 * Maps directly to Kafka topic routing in NotificationProducer.
 */
public enum NotificationChannel {
    EMAIL,
    SMS,
    IN_APP
}