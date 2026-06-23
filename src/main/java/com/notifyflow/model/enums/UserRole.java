package com.notifyflow.model.enums;

/**
 * Application roles.
 * Prefixed with ROLE_ at the Spring Security layer via UserDetailsService.
 */
public enum UserRole {
    USER,
    ADMIN
}