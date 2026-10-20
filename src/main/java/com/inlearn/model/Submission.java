package com.inlearn.model;

import java.time.Instant;

/**
 * A student's current submission for an assignment. Replacing a submission updates this record
 * and increments {@code attemptNumber}.
 *
 * @param late whether the file arrived after the due date
 */
public record Submission(
        long id,
        long assignmentId,
        long studentId,
        String studentName,
        String originalFilename,
        String storageKey,
        long fileSizeBytes,
        Instant submittedAt,
        boolean late,
        int attemptNumber,
        int version) {
}
