package com.inlearn.model;

/**
 * Archived courses are read-only: their history stays visible but nothing new can be added.
 */
public enum CourseStatus {
    ACTIVE("Active"),
    ARCHIVED("Archived");

    private final String displayName;

    CourseStatus(String displayName) {
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
