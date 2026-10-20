package com.inlearn.model;

/**
 * Course materials are either uploaded files or links to web resources.
 */
public enum MaterialType {
    FILE("File"),
    LINK("Web link");

    private final String displayName;

    MaterialType(String displayName) {
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
