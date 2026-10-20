package com.inlearn.model;

import java.time.Instant;
import java.util.List;

/**
 * Data for the instructor dashboard.
 *
 * @param activeStudentCount   distinct students actively enrolled across all assigned courses
 * @param awaitingGrading      the oldest ungraded submissions (a limited list)
 * @param awaitingGradingTotal the total number of ungraded submissions
 */
public record InstructorDashboard(
        List<Course> courses,
        int activeStudentCount,
        List<UpcomingAssignment> upcomingAssignments,
        List<PendingSubmission> awaitingGrading,
        int awaitingGradingTotal) {

    /** An assignment that is not yet due, with how many enrolled students have submitted. */
    public record UpcomingAssignment(
            long assignmentId,
            long courseId,
            String courseCode,
            String title,
            Instant dueAt,
            int submissionCount,
            int enrolledCount) {
    }

    /** A submission that has not been graded yet. */
    public record PendingSubmission(
            long submissionId,
            long assignmentId,
            long courseId,
            String courseCode,
            String assignmentTitle,
            String studentName,
            Instant submittedAt,
            boolean late) {
    }
}
