package com.inlearn.model;

/**
 * The three kinds of InLearn account. A user has exactly one role.
 */
public enum Role {
    ADMIN("Administrator"),
    INSTRUCTOR("Instructor"),
    STUDENT("Student");

    private final String displayName;

    Role(String displayName) {
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
