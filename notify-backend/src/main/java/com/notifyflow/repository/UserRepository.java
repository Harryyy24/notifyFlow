package com.notifyflow.repository;

import com.notifyflow.model.entity.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for {@link UserEntity}.
 *
 * Spring Data JPA generates all query implementations at startup.
 * Custom JPQL query used for the existence check to avoid loading
 * the full entity just to check email uniqueness during registration.
 */
@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    /**
     * Finds a user by email address.
     * Used by Spring Security's UserDetailsService on every
     * authenticated request to load the principal.
     *
     * @param email the email address to look up
     * @return Optional containing the user if found
     */
    Optional<UserEntity> findByEmail(String email);

    /**
     * Checks whether a user with the given email already exists.
     * More efficient than findByEmail() for registration validation
     * since it avoids loading the full entity + password hash.
     *
     * @param email the email address to check
     * @return true if a user with this email exists
     */
    @Query("SELECT COUNT(u) > 0 FROM UserEntity u WHERE u.email = :email")
    boolean existsByEmail(@Param("email") String email);
}