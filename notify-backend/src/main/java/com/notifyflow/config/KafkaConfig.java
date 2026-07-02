package com.notifyflow.config;

import com.notifyflow.kafka.event.NotificationEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka producer, consumer, and topic configuration.
 *
 * Topics created here:
 *   notifyflow.email      notifyflow.email.dlt
 *   notifyflow.sms        notifyflow.sms.dlt
 *   notifyflow.inapp      notifyflow.inapp.dlt
 *
 * Error handling strategy:
 *   Retry 3 times with 1s fixed backoff →
 *   on exhaustion, publish to DLT via DeadLetterPublishingRecoverer
 */
@Configuration
@ConditionalOnProperty(name = "app.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${app.kafka.partitions}")
    private int partitions;

    @Value("${app.kafka.replication-factor}")
    private short replicationFactor;

    @Value("${app.kafka.topics.email}")
    private String emailTopic;

    @Value("${app.kafka.topics.sms}")
    private String smsTopic;

    @Value("${app.kafka.topics.in-app}")
    private String inAppTopic;

    @Value("${app.notification.retry-attempts}")
    private int retryAttempts;

    @Value("${app.notification.retry-backoff-ms}")
    private long retryBackoffMs;

    // ── Topic Definitions ──────────────────────────────────────────

    @Bean
    public NewTopic emailTopic() {
        return TopicBuilder.name(emailTopic)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    public NewTopic smsTopic() {
        return TopicBuilder.name(smsTopic)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    public NewTopic inAppTopic() {
        return TopicBuilder.name(inAppTopic)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }

    // ── Dead Letter Topics ─────────────────────────────────────────

    @Bean
    public NewTopic emailDltTopic() {
        return TopicBuilder.name(emailTopic + ".dlt")
                .partitions(1)           // DLT needs only 1 partition — low volume
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    public NewTopic smsDltTopic() {
        return TopicBuilder.name(smsTopic + ".dlt")
                .partitions(1)
                .replicas(replicationFactor)
                .build();
    }

    @Bean
    public NewTopic inAppDltTopic() {
        return TopicBuilder.name(inAppTopic + ".dlt")
                .partitions(1)
                .replicas(replicationFactor)
                .build();
    }

    // ── Producer ───────────────────────────────────────────────────

    @Bean
    public ProducerFactory<String, NotificationEvent> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JsonSerializer.class);
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        config.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1);
        // Don't add Spring type headers — keeps messages broker-agnostic
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, NotificationEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    // ── Consumer ───────────────────────────────────────────────────

    @Bean
    public ConsumerFactory<String, NotificationEvent> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG,
                "notifyflow-consumer-group");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                JsonDeserializer.class);

        JsonDeserializer<NotificationEvent> deserializer =
                new JsonDeserializer<>(NotificationEvent.class, false);
        deserializer.addTrustedPackages("com.notifyflow.kafka.event");

        return new DefaultKafkaConsumerFactory<>(
                config,
                new StringDeserializer(),
                deserializer
        );
    }

    /**
     * Container factory with:
     * - MANUAL_IMMEDIATE ack mode (consumer controls offset commits)
     * - DefaultErrorHandler with FixedBackOff (3 retries, 1s gap)
     * - DeadLetterPublishingRecoverer (publishes to .dlt topic on exhaustion)
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NotificationEvent>
    kafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, NotificationEvent>
                factory = new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        factory.getContainerProperties()
                .setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // Error handler: retry 3x with 1s backoff, then DLT
        factory.setCommonErrorHandler(errorHandler());

        return factory;
    }

    /**
     * DefaultErrorHandler replaces the deprecated SeekToCurrentErrorHandler.
     * On retry exhaustion, DeadLetterPublishingRecoverer routes the failed
     * message to {originalTopic}.dlt automatically.
     */
    @Bean
    public DefaultErrorHandler errorHandler() {
        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(kafkaTemplate());

        FixedBackOff backOff = new FixedBackOff(retryBackoffMs, retryAttempts);

        DefaultErrorHandler handler =
                new DefaultErrorHandler(recoverer, backOff);

        // Don't retry on deserialization errors — they'll never succeed
        handler.addNotRetryableExceptions(
                org.apache.kafka.common.errors.SerializationException.class,
                com.fasterxml.jackson.core.JsonProcessingException.class
        );

        return handler;
    }
}