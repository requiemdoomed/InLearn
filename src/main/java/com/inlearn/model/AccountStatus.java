package com.inlearn.model;

/**
 * Inactive accounts keep all of their academic history but cannot sign in.
 */
public enum AccountStatus {
    ACTIVE("Active"),
    INACTIVE("Inactive");

    private final String displayName;

    AccountStatus(String displayName) {
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
