package com.inlearn.model;

import java.time.Instant;

/**
 * An InLearn account. The password hash is deliberately not part of this record so it never
 * travels into the user interface; see {@link UserCredentials}.
 *
 * @param version optimistic-locking version, used to detect concurrent edits
 */
public record User(
        long id,
        String email,
        String fullName,
        Role role,
        AccountStatus status,
        ThemePreference themePreference,
        Instant lastLoginAt,
        int version,
        Instant createdAt) {

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public boolean hasRole(Role expectedRole) {
        return role == expectedRole;
    }
}
