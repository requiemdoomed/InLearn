package com.inlearn.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A piece of course work that students submit as a file.
 *
 * @param dueAt        deadline as an instant (stored as UTC)
 * @param lateCutoffAt last moment late work is accepted; {@code null} means no cutoff
 */
public record Assignment(
        long id,
        long courseId,
        String title,
        String instructions,
        Instant dueAt,
        BigDecimal maxPoints,
        LatePolicy latePolicy,
        Instant lateCutoffAt,
        int version,
        Instant createdAt) {

    public boolean isPastDue(Instant now) {
        return now.isAfter(dueAt);
    }
}
