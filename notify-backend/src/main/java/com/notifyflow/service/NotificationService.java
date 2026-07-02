package com.notifyflow.service;

import com.notifyflow.dto.NotificationRequestDTO;
import com.notifyflow.dto.NotificationResponseDTO;
import com.notifyflow.dto.PagedResponseDTO;
import com.notifyflow.dto.StatusUpdateRequestDTO;
import com.notifyflow.exception.DuplicateNotificationException;
import com.notifyflow.exception.QuietHoursActiveException;
import com.notifyflow.exception.ResourceNotFoundException;
import com.notifyflow.kafka.producer.NotificationProducer;
import com.notifyflow.model.entity.NotificationEntity;
import com.notifyflow.model.entity.UserEntity;
import com.notifyflow.model.enums.NotificationPriority;
import com.notifyflow.model.enums.NotificationStatus;
import com.notifyflow.repository.NotificationRepository;
import com.notifyflow.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.notifyflow.dto.NotificationStatsDTO;
import com.notifyflow.model.enums.NotificationChannel;

/**
 * Core notification service — orchestrates the send pipeline:
 *
 *   1. Validate user exists
 *   2. Check channel enabled in user preferences
 *   3. Check quiet hours for LOW priority notifications
 *   4. Check Redis deduplication window
 *   5. Persist NotificationEntity with PENDING status
 *   6. Publish NotificationEvent to Kafka
 *   7. Return 202 Accepted with notification ID
 *
 * History retrieval is cached in Redis per (userId, page, size).
 * Cache is evicted when a new notification is sent to that user.
 */
@Slf4j
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository         userRepository;
    private final PreferenceService      preferenceService;

    @Autowired(required = false)
    private DeduplicationService deduplicationService;

    @Autowired(required = false)
    private NotificationProducer notificationProducer;

    public NotificationService(
            NotificationRepository notificationRepository,
            UserRepository userRepository,
            PreferenceService preferenceService) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.preferenceService = preferenceService;
    }

    // ── Send Pipeline ──────────────────────────────────────────────

    /**
     * Processes a notification send request through the full pipeline.
     * Evicts the history cache for this user so the next GET
     * reflects the newly created notification.
     *
     * @param request the notification request DTO
     * @return response DTO with notificationId and PENDING status
     * @throws ResourceNotFoundException    if the user doesn't exist
     * @throws DuplicateNotificationException if within dedup window
     * @throws QuietHoursActiveException    if quiet hours block LOW priority
     */
    @Transactional
    @CacheEvict(value = "notification-history", allEntries = true, beforeInvocation = true)
    public NotificationResponseDTO sendNotification(
            NotificationRequestDTO request) {

        log.info("Processing notification send — userId=[{}] channel=[{}] " +
                        "priority=[{}]", request.getUserId(),
                request.getChannel(), request.getPriority());

        // Step 1 — Verify user exists
        UserEntity user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + request.getUserId()));

        // Step 2 — Check channel is enabled for this user
        if (!preferenceService.isChannelEnabled(
                request.getUserId(), request.getChannel())) {
            throw new ResourceNotFoundException(
                    "Channel " + request.getChannel() +
                            " is disabled for userId: " + request.getUserId());
        }

        // Step 3 — Quiet hours check (only blocks LOW priority)
        if (request.getPriority() == NotificationPriority.LOW
                && preferenceService.isQuietHoursActive(request.getUserId())) {
            log.info("Quiet hours active — blocking LOW priority notification " +
                    "for userId=[{}]", request.getUserId());
            throw new QuietHoursActiveException(
                    "Quiet hours are active. LOW priority notifications are " +
                            "suppressed until the quiet window ends.");
        }

        // Step 4 — Deduplication check (skipped if Redis is disabled)
        if (deduplicationService != null
                && deduplicationService.isDuplicate(
                        request.getUserId(),
                        request.getChannel(),
                        request.getTitle(),
                        request.getMessage())) {

            long ttlSeconds = deduplicationService.getRemainingTtlSeconds(
                    request.getUserId(),
                    request.getChannel(),
                    request.getTitle(),
                    request.getMessage());

            throw new DuplicateNotificationException(
                    String.format(
                            "Duplicate notification detected. " +
                                    "This message was already sent within the last %d minutes. " +
                                    "Retry allowed in %d seconds.",
                            10, ttlSeconds));
        }

        // Step 5 — Persist with PENDING status
        NotificationEntity entity = NotificationEntity.builder()
                .user(user)
                .channel(request.getChannel())
                .title(request.getTitle())
                .message(request.getMessage())
                .status(NotificationStatus.PENDING)
                .priority(request.getPriority())
                .retryCount(0)
                .build();

        NotificationEntity saved = notificationRepository.save(entity);
        log.debug("Notification persisted — id=[{}]", saved.getId());

        // Step 6 — Publish to Kafka (skipped if Kafka is disabled)
        if (notificationProducer != null) {
            notificationProducer.publishNotification(saved);
        } else {
            log.warn("Kafka disabled — notification id=[{}] will remain PENDING. " +
                    "Manually mark as DELIVERED via admin panel.", saved.getId());
        }

        log.info("Notification accepted — id=[{}] userId=[{}] channel=[{}]",
                saved.getId(), request.getUserId(), request.getChannel());

        // Step 7 — Return 202 Accepted
        return mapToDTO(saved);
    }

    // ── History ────────────────────────────────────────────────────

    /**
     * Retrieves paginated notification history for a user.
     * Results are cached in Redis for 5 minutes.
     *
     * Cache key: notification-history::{userId}:{page}:{size}
     *
     * @param userId the target user's ID
     * @param page   zero-based page index
     * @param size   number of items per page (default 20)
     * @return paginated list of notifications
     * @throws ResourceNotFoundException if the user doesn't exist
     */
    @Cacheable(
            value = "notification-history",
            key   = "#userId + ':' + #page + ':' + #size"
    )
    @Transactional(readOnly = true)
    public PagedResponseDTO<NotificationResponseDTO> getHistory(
            Long userId, int page, int size) {

        log.debug("Loading notification history from DB — " +
                "userId=[{}] page=[{}] size=[{}]", userId, page, size);

        // Verify user exists before querying history
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException(
                    "User not found with id: " + userId);
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<NotificationEntity> entityPage =
                notificationRepository
                        .findByUserIdOrderByCreatedAtDesc(userId, pageable);

        return PagedResponseDTO.<NotificationResponseDTO>builder()
                .content(entityPage.getContent()
                        .stream()
                        .map(this::mapToDTO)
                        .toList())
                .page(entityPage.getNumber())
                .size(entityPage.getSize())
                .totalElements(entityPage.getTotalElements())
                .totalPages(entityPage.getTotalPages())
                .first(entityPage.isFirst())
                .last(entityPage.isLast())
                .build();
    }

    // ── Status Queries ─────────────────────────────────────────────

    /**
     * Retrieves the current delivery status of a single notification.
     *
     * @param notificationId the notification ID
     * @return response DTO with current status
     * @throws ResourceNotFoundException if notification not found
     */
    @Transactional(readOnly = true)
    public NotificationResponseDTO getStatus(Long notificationId) {
        return notificationRepository
                .findByIdWithUser(notificationId)
                .map(this::mapToDTO)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Notification not found with id: " + notificationId));
    }

    // ── Admin Operations ───────────────────────────────────────────

    /**
     * Manually updates the delivery status of a notification.
     * ADMIN-only operation — enforced at the controller level
     * via @PreAuthorize("hasRole('ADMIN')").
     *
     * @param notificationId the notification ID to update
     * @param request        contains the new status
     * @return updated response DTO
     * @throws ResourceNotFoundException if notification not found
     */
    @Transactional
    public NotificationResponseDTO updateStatus(
            Long notificationId,
            StatusUpdateRequestDTO request) {

        NotificationEntity entity =
                notificationRepository.findByIdWithUser(notificationId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Notification not found with id: " + notificationId));

        entity.setStatus(request.getStatus());

        if (request.getStatus() == NotificationStatus.DELIVERED) {
            entity.setDeliveredAt(LocalDateTime.now());
        }

        NotificationEntity updated = notificationRepository.save(entity);
        log.info("Status manually updated — id=[{}] newStatus=[{}]",
                notificationId, request.getStatus());

        return mapToDTO(updated);
    }

    // ── Consumer Callback ──────────────────────────────────────────

    /**
     * Called by Kafka consumers after processing a notification.
     * Updates the delivery status and Kafka offset in the DB.
     *
     * This method is intentionally package-accessible rather than
     * public — only the consumer layer should call it.
     *
     * @param notificationId the notification that was processed
     * @param kafkaOffset    the Kafka partition offset of the message
     * @param success        true if delivery succeeded, false if failed
     */
    @Transactional
    @CacheEvict(value = "notification-history", allEntries = true)
    public void markDelivered(Long notificationId,
                              Long kafkaOffset,
                              boolean success) {
        notificationRepository.findById(notificationId).ifPresent(entity -> {
            if (success) {
                entity.markDelivered(kafkaOffset);
                log.info("Notification delivered — id=[{}] offset=[{}]",
                        notificationId, kafkaOffset);
            } else {
                entity.markFailed();
                log.warn("Notification failed — id=[{}] retryCount=[{}]",
                        notificationId, entity.getRetryCount());
            }
            notificationRepository.save(entity);
        });
    }

    // ── Analytics ──────────────────────────────────────────────────

    /**
     * Returns aggregated notification stats, optionally filtered by last N days.
     */
    @Transactional(readOnly = true)
    public NotificationStatsDTO getStats(Integer days) {
        LocalDateTime after = days != null ? LocalDateTime.now().minusDays(days) : null;

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (NotificationStatus status : NotificationStatus.values()) {
            long count = after != null
                ? notificationRepository.countByStatusAndCreatedAtAfter(status, after)
                : notificationRepository.countByStatus(status);
            byStatus.put(status.name(), count);
        }

        Map<String, Long> byChannel = new LinkedHashMap<>();
        for (NotificationChannel channel : NotificationChannel.values()) {
            long count = notificationRepository.countByChannel(channel);
            byChannel.put(channel.name(), count);
        }

        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();

        return NotificationStatsDTO.builder()
                .total(total)
                .byStatus(byStatus)
                .byChannel(byChannel)
                .build();
    }

    // ── Mapping ────────────────────────────────────────────────────

    /**
     * Maps a NotificationEntity to its response DTO.
     * Centralised here to keep controller and consumer code clean.
     */
    public NotificationResponseDTO mapToDTO(NotificationEntity entity) {
        return NotificationResponseDTO.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .channel(entity.getChannel())
                .title(entity.getTitle())
                .message(entity.getMessage())
                .status(entity.getStatus())
                .priority(entity.getPriority())
                .kafkaOffset(entity.getKafkaOffset())
                .retryCount(entity.getRetryCount())
                .createdAt(entity.getCreatedAt())
                .deliveredAt(entity.getDeliveredAt())
                .build();
    }
}