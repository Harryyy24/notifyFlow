package com.notifyflow.kafka.consumer;

import com.notifyflow.kafka.event.NotificationEvent;
import com.notifyflow.model.entity.UserEntity;
import com.notifyflow.repository.UserRepository;
import com.notifyflow.service.EmailSenderService;
import com.notifyflow.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for EMAIL channel notifications.
 *
 * Listens on: notifyflow.email (3 partitions)
 * Consumer group: notifyflow-consumer-group
 * Concurrency: 3 threads (one per partition — set in KafkaConfig)
 *
 * Processing model:
 *   1. Receive NotificationEvent from Kafka
 *   2. Lookup the recipient's email address from the database
 *   3. Send a real email via SMTP (JavaMailSender)
 *   4. Update MySQL delivery status via NotificationService
 *   5. Manually acknowledge offset (MANUAL_IMMEDIATE mode)
 *
 * Error handling:
 *   DefaultErrorHandler in KafkaConfig retries 3x with 1s backoff.
 *   After exhaustion, DeadLetterPublishingRecoverer routes to
 *   notifyflow.email.dlt. This consumer does NOT catch exceptions —
 *   it lets them propagate to the error handler intentionally.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class EmailConsumer {

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final EmailSenderService emailSenderService;

    /**
     * Processes an email notification event.
     *
     * @param record  the full Kafka ConsumerRecord (includes offset metadata)
     * @param ack     manual acknowledgement handle
     * @param partition the partition this message was consumed from
     * @param offset    the message offset within the partition
     */
    @KafkaListener(
            topics         = "${app.kafka.topics.email}",
            groupId        = "notifyflow-consumer-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(
            ConsumerRecord<String, NotificationEvent> record,
            Acknowledgment ack,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET)             long offset) {

        NotificationEvent event = record.value();

        log.info("EMAIL consumer received — notificationId=[{}] " +
                        "userId=[{}] partition=[{}] offset=[{}]",
                event.getNotificationId(), event.getUserId(),
                partition, offset);

        try {
            // Step 1 — Lookup the recipient's email address
            UserEntity user = userRepository.findById(event.getUserId())
                    .orElseThrow(() -> new RuntimeException(
                            "User not found for notificationId=[" +
                                    event.getNotificationId() + "] userId=[" +
                                    event.getUserId() + "]"));

            // Step 2 — Send the email via SMTP
            emailSenderService.sendEmail(
                    user.getEmail(), event.getTitle(), event.getMessage());

            log.info("EMAIL sent successfully — notificationId=[{}] " +
                            "userId=[{}] to=[{}]",
                    event.getNotificationId(), event.getUserId(),
                    user.getEmail());

            // Step 3 — Mark as delivered in MySQL
            notificationService.markDelivered(
                    event.getNotificationId(), offset, true);

            // Step 4 — Commit offset only after successful processing
            ack.acknowledge();

            log.info("EMAIL delivered successfully — notificationId=[{}] " +
                            "userId=[{}] offset=[{}]",
                    event.getNotificationId(), event.getUserId(), offset);

        } catch (Exception e) {
            log.error("EMAIL processing failed — notificationId=[{}] " +
                            "userId=[{}] error=[{}]",
                    event.getNotificationId(), event.getUserId(),
                    e.getMessage(), e);
            throw new RuntimeException(
                    "Email delivery failed for notificationId=[" +
                            event.getNotificationId() + "]", e);
        }
    }
}