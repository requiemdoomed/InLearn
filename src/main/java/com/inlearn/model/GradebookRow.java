package com.inlearn.model;

import java.util.Map;

/**
 * One student's line in an instructor's course gradebook.
 *
 * @param results     each assignment's result keyed by assignment id; missing keys mean no submission
 * @param courseGrade the student's current grade from graded work only
 */
public record GradebookRow(
        long studentId,
        String studentName,
        String studentEmail,
        Map<Long, AssignmentResult> results,
        CourseGrade courseGrade) {

    /** A submission and its grade; either part may be {@code null}. */
    public record AssignmentResult(Submission submission, Grade grade) {
    }
}
