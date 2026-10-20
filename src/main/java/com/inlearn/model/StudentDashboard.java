package com.inlearn.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Data for the student dashboard.
 */
public record StudentDashboard(
        List<Course> courses,
        List<Deadline> deadlines,
        List<Announcement> recentAnnouncements,
        List<RecentGrade> recentGrades) {

    /** An assignment the student still needs to act on (upcoming or overdue). */
    public record Deadline(
            long assignmentId,
            long courseId,
            String courseCode,
            String title,
            Instant dueAt,
            AssignmentProgress progress) {
    }

    /** A recently graded assignment. */
    public record RecentGrade(
            long assignmentId,
            long courseId,
            String courseCode,
            String assignmentTitle,
            BigDecimal points,
            BigDecimal maxPoints,
            Instant gradedAt) {
    }
}
