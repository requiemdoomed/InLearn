package com.inlearn.model;

/**
 * Where a student stands on one assignment. Labels always carry the meaning in text so the
 * status never depends on colour alone.
 */
public enum AssignmentProgress {
    NOT_SUBMITTED("Not submitted"),
    OVERDUE("Overdue"),
    SUBMITTED("Submitted · Awaiting feedback"),
    SUBMITTED_LATE("Submitted late · Awaiting feedback"),
    GRADED("Graded");

    private final String label;

    AssignmentProgress(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
