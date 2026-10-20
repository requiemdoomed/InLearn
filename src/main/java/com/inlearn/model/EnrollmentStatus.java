package com.inlearn.model;

/**
 * Withdrawing keeps the enrollment row (and the student's work) for the academic record.
 */
public enum EnrollmentStatus {
    ACTIVE("Enrolled"),
    WITHDRAWN("Withdrawn");

    private final String displayName;

    EnrollmentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
