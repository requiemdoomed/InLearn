package com.inlearn.model;

import java.time.Instant;

/**
 * A student's membership in a course, including the names needed to display it.
 */
public record Enrollment(
        long id,
        long courseId,
        String courseCode,
        String courseTitle,
        long studentId,
        String studentName,
        String studentEmail,
        EnrollmentStatus status,
        Instant enrolledAt,
        Instant withdrawnAt,
        int version) {

    public boolean isActive() {
        return status == EnrollmentStatus.ACTIVE;
    }
}
