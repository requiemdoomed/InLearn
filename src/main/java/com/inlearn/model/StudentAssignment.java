package com.inlearn.model;

/**
 * An assignment as one student sees it: the assignment itself, their current submission and
 * grade (either may be {@code null}), and the resulting progress state.
 */
public record StudentAssignment(
        Assignment assignment,
        Submission submission,
        Grade grade,
        AssignmentProgress progress) {

    public boolean hasSubmission() {
        return submission != null;
    }

    public boolean isGraded() {
        return grade != null;
    }
}
