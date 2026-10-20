package com.inlearn.ui.theme;

import com.inlearn.model.ThemePreference;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Settings stored on this computer rather than in the database: the appearance of the sign-in
 * screen and, if the user opted in, the email address to prefill there. Passwords are never stored.
 *
 * <p>Uses {@link java.util.prefs.Preferences}, which the JDK stores in the registry on Windows,
 * a plist on macOS and {@code ~/.java} on Linux.
 */
public class LocalPreferences {

    private static final Logger LOGGER = Logger.getLogger(LocalPreferences.class.getName());
    private static final String SIGNED_OUT_THEME_KEY = "signedOutTheme";
    private static final String REMEMBERED_EMAIL_KEY = "rememberedEmail";

    private final Preferences preferences;

    public LocalPreferences() {
        this(Preferences.userRoot().node("com/inlearn"));
    }

    LocalPreferences(Preferences preferences) {
        this.preferences = preferences;
    }

    public ThemePreference signedOutTheme() {
        try {
            return ThemePreference.fromStoredValue(preferences.get(SIGNED_OUT_THEME_KEY, ThemePreference.SYSTEM.name()));
        } catch (IllegalStateException | SecurityException e) {
            LOGGER.log(Level.WARNING, "Could not read local preferences; using the system appearance", e);
            return ThemePreference.SYSTEM;
        }
    }

    public void saveSignedOutTheme(ThemePreference preference) {
        try {
            preferences.put(SIGNED_OUT_THEME_KEY, preference.name());
            preferences.flush();
        } catch (BackingStoreException | IllegalStateException | SecurityException e) {
            // The theme still changes for this session; it just won't be remembered next time.
            LOGGER.log(Level.WARNING, "Could not save the sign-in screen appearance", e);
        }
    }

    /** The email to prefill on the sign-in screen, or an empty string. */
    public String rememberedEmail() {
        try {
            return preferences.get(REMEMBERED_EMAIL_KEY, "");
        } catch (IllegalStateException | SecurityException e) {
            LOGGER.log(Level.WARNING, "Could not read the remembered email", e);
            return "";
        }
    }

    /** Remembers an email for the sign-in screen, or forgets it when {@code email} is null. */
    public void setRememberedEmail(String email) {
        try {
            if (email == null || email.isBlank()) {
                preferences.remove(REMEMBERED_EMAIL_KEY);
            } else {
                preferences.put(REMEMBERED_EMAIL_KEY, email.strip());
            }
            preferences.flush();
        } catch (BackingStoreException | IllegalStateException | SecurityException e) {
            LOGGER.log(Level.WARNING, "Could not save the remembered email", e);
        }
    }
}
