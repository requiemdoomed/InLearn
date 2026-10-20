package com.inlearn.model;

/**
 * One enrolled student on the grading screen for an assignment.
 *
 * @param submission the student's current submission, or {@code null} if they have not submitted
 * @param grade      the grade for that submission, or {@code null} if it has not been graded
 */
public record SubmissionReviewRow(
        long studentId,
        String studentName,
        String studentEmail,
        Submission submission,
        Grade grade) {

    public boolean hasSubmission() {
        return submission != null;
    }

    public boolean isGraded() {
        return grade != null;
    }

}
