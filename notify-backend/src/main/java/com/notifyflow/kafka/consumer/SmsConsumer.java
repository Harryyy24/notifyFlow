package com.notifyflow.kafka.consumer;

import com.notifyflow.kafka.event.NotificationEvent;
import com.notifyflow.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Kafka consumer for SMS channel notifications.
 *
 * Listens on: notifyflow.sms (3 partitions)
 * Consumer group: notifyflow-consumer-group
 *
 * Identical processing model to EmailConsumer.
 * In production, replace simulateProcessing() with an
 * actual SMS provider call (Twilio, AWS SNS, etc.)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class SmsConsumer {

    private final NotificationService notificationService;

    @Value("${app.notification.failure-rate}")
    private double failureRate;

    private static final int MIN_PROCESSING_MS = 200;
    private static final int MAX_PROCESSING_MS = 500;

    @KafkaListener(
            topics           = "${app.kafka.topics.sms}",
            groupId          = "notifyflow-consumer-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            ConsumerRecord<String, NotificationEvent> record,
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET)             long offset) {

        NotificationEvent event = record.value();

        log.info("SMS consumer received — notificationId=[{}] " +
                        "userId=[{}] partition=[{}] offset=[{}]",
                event.getNotificationId(), event.getUserId(),
                partition, offset);

        try {
            simulateProcessing();

            if (shouldSimulateFailure()) {
                throw new RuntimeException(
                        "Simulated SMS delivery failure for " +
                                "notificationId=[" + event.getNotificationId() + "]");
            }

            notificationService.markDelivered(
                    event.getNotificationId(), offset, true);

            ack.acknowledge();

            log.info("SMS delivered successfully — notificationId=[{}] " +
                            "userId=[{}] offset=[{}]",
                    event.getNotificationId(), event.getUserId(), offset);

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(
                    "SMS processing interrupted for notificationId=[" +
                            event.getNotificationId() + "]", ie);
        }
    }

    private void simulateProcessing() throws InterruptedException {
        long delay = ThreadLocalRandom.current()
                .nextLong(MIN_PROCESSING_MS, MAX_PROCESSING_MS + 1);
        Thread.sleep(delay);
        log.debug("SMS processing simulated — delay=[{}ms]", delay);
    }

    private boolean shouldSimulateFailure() {
        return ThreadLocalRandom.current().nextDouble() < failureRate;
    }
}