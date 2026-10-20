package com.inlearn.model;

/**
 * What happens when a student submits after the due date.
 */
public enum LatePolicy {
    /** The submit button closes at the deadline. */
    NO_LATE("Late submissions not accepted"),
    /** Submissions after the deadline are accepted and marked late (optionally until a cutoff). */
    ALLOW_LATE("Accept late submissions (marked late)");

    private final String displayName;

    LatePolicy(String displayName) {
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
