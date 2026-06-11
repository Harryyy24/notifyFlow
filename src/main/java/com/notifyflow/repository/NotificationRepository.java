package com.notifyflow.repository;

import com.notifyflow.model.entity.NotificationEntity;
import com.notifyflow.model.enums.NotificationChannel;
import com.notifyflow.model.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link NotificationEntity}.
 *
 * All paginated queries use Spring Data's {@link Pageable} abstraction,
 * which translates to LIMIT / OFFSET in generated SQL.
 * The JOIN FETCH in history queries prevents N+1 on the user association.
 */
@Repository
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    /**
     * Paginated notification history for a single user, newest first.
     * Used by GET /api/notifications/{userId}/history.
     *
     * JOIN FETCH loads the user in the same query to avoid N+1 when
     * the controller maps the entity to a DTO that includes userId.
     *
     * @param userId   the user whose notifications to retrieve
     * @param pageable page + size + sort from the request params
     * @return a page of notifications ordered by createdAt DESC
     */
    @Query("""
            SELECT n FROM NotificationEntity n
            JOIN FETCH n.user u
            WHERE u.id = :userId
            ORDER BY n.createdAt DESC
            """)
    Page<NotificationEntity> findByUserIdOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            Pageable pageable);

    /**
     * Finds a single notification by ID, eagerly fetching the user.
     * Used by the status endpoint to avoid a lazy-load outside a transaction.
     *
     * @param id the notification ID
     * @return Optional containing the notification with user loaded
     */
    @Query("""
            SELECT n FROM NotificationEntity n
            JOIN FETCH n.user u
            WHERE n.id = :id
            """)
    Optional<NotificationEntity> findByIdWithUser(@Param("id") Long id);

    /**
     * Bulk status update — used by admin operations and retry jobs.
     * @Modifying + @Transactional required for UPDATE/DELETE JPQL queries.
     *
     * @param id     the notification ID to update
     * @param status the new status to set
     * @return number of rows affected (0 or 1)
     */
    @Modifying
    @Query("""
            UPDATE NotificationEntity n
            SET n.status = :status
            WHERE n.id = :id
            """)
    int updateStatus(
            @Param("id") Long id,
            @Param("status") NotificationStatus status);

    /**
     * Finds all notifications for a user filtered by channel.
     * Useful for analytics and per-channel history views.
     *
     * @param userId  the target user
     * @param channel the delivery channel filter
     * @param pageable pagination params
     * @return paginated notifications for the given user and channel
     */
    @Query("""
            SELECT n FROM NotificationEntity n
            JOIN FETCH n.user u
            WHERE u.id = :userId
            AND n.channel = :channel
            ORDER BY n.createdAt DESC
            """)
    Page<NotificationEntity> findByUserIdAndChannel(
            @Param("userId") Long userId,
            @Param("channel") NotificationChannel channel,
            Pageable pageable);

    /**
     * Counts notifications by status for a user.
     * Used to build delivery stats summaries.
     *
     * @param userId the target user
     * @param status the status to count
     * @return count of matching notifications
     */
    long countByUserIdAndStatus(Long userId, NotificationStatus status);

    /**
     * Finds PENDING notifications older than a given timestamp.
     * Intended for a future scheduled retry job.
     *
     * @param status    typically PENDING
     * @param threshold notifications created before this time
     * @return list of stale pending notifications
     */
    @Query("""
            SELECT n FROM NotificationEntity n
            JOIN FETCH n.user u
            WHERE n.status = :status
            AND n.createdAt < :threshold
            ORDER BY n.createdAt ASC
            """)
    List<NotificationEntity> findStaleNotifications(
            @Param("status") NotificationStatus status,
            @Param("threshold") LocalDateTime threshold);
}