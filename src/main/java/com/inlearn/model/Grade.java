package com.inlearn.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The instructor's mark and written feedback for one submission.
 */
public record Grade(
        long id,
        long submissionId,
        BigDecimal points,
        String feedback,
        long gradedById,
        String gradedByName,
        Instant gradedAt,
        int version) {
}
