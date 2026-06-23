package com.notifyflow.repository;

import com.notifyflow.model.entity.UserPreferenceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link UserPreferenceEntity}.
 *
 * Lookup is always by userId since there is exactly one preference
 * row per user (enforced by UNIQUE constraint in V3 migration).
 */
@Repository
public interface PreferenceRepository extends JpaRepository<UserPreferenceEntity, Long> {

    /**
     * Finds preferences by the owning user's ID.
     * JOIN FETCH loads the user association in a single query.
     *
     * @param userId the user whose preferences to retrieve
     * @return Optional containing preferences if they exist
     */
    @Query("""
            SELECT p FROM UserPreferenceEntity p
            JOIN FETCH p.user u
            WHERE u.id = :userId
            """)
    Optional<UserPreferenceEntity> findByUserId(@Param("userId") Long userId);

    /**
     * Checks whether preferences exist for a user.
     * Used during the PUT endpoint to decide between insert and update.
     *
     * @param userId the user ID to check
     * @return true if a preference row already exists
     */
    @Query("SELECT COUNT(p) > 0 FROM UserPreferenceEntity p WHERE p.user.id = :userId")
    boolean existsByUserId(@Param("userId") Long userId);
}