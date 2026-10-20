package com.inlearn.model;

/**
 * A user's chosen appearance. {@link #SYSTEM} follows the operating system's light or dark setting.
 */
public enum ThemePreference {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark");

    private final String displayName;

    ThemePreference(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Parses a stored value, falling back to {@link #SYSTEM} for anything unrecognised. */
    public static ThemePreference fromStoredValue(String value) {
        if (value == null) {
            return SYSTEM;
        }
        for (ThemePreference preference : values()) {
            if (preference.name().equalsIgnoreCase(value.trim())) {
                return preference;
            }
        }
        return SYSTEM;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
