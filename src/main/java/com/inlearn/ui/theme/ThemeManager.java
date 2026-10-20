package com.inlearn.ui.theme;

import com.inlearn.model.ThemePreference;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.stage.Window;

import java.net.URL;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The single place that decides which colours the application uses.
 *
 * <p>How theming works:
 * <ul>
 *   <li>{@code components.css} styles every control using looked-up colours such as
 *       {@code -il-surface} and {@code -il-text}, and never contains literal colours.</li>
 *   <li>{@code theme-light.css} and {@code theme-dark.css} only define those looked-up colours
 *       (plus JavaFX's own Modena base colours) on {@code .root}.</li>
 *   <li>Switching theme swaps the palette stylesheet on every open window. Nothing is reloaded,
 *       so the current page, typed text and selections are all preserved.</li>
 * </ul>
 *
 * <p>Every application window, dialog, popup and tooltip is styled automatically: the manager
 * watches {@link Window#getWindows()} and applies the stylesheets to each new window's scene.
 *
 * <p>{@link ThemePreference#SYSTEM} uses JavaFX's {@link Platform.Preferences#getColorScheme()},
 * which reports the operating system's light/dark setting on Windows, macOS and most Linux
 * desktops and updates live when the user changes it. If the platform cannot report a colour
 * scheme, InLearn falls back to the light theme and the profile screen says so.
 */
public final class ThemeManager {

    /** The palette actually on screen. */
    public enum AppliedTheme { LIGHT, DARK }

    private static final Logger LOGGER = Logger.getLogger(ThemeManager.class.getName());
    private static final String COMPONENTS_STYLESHEET = stylesheet("components.css");
    private static final String LIGHT_PALETTE = stylesheet("theme-light.css");
    private static final String DARK_PALETTE = stylesheet("theme-dark.css");
    private static final AppliedTheme SYSTEM_FALLBACK_THEME = AppliedTheme.LIGHT;

    private final ReadOnlyObjectWrapper<ThemePreference> preference =
            new ReadOnlyObjectWrapper<>(ThemePreference.SYSTEM);
    private final ReadOnlyObjectWrapper<AppliedTheme> appliedTheme =
            new ReadOnlyObjectWrapper<>(SYSTEM_FALLBACK_THEME);
    private final Platform.Preferences platformPreferences;

    /** Must be created on the JavaFX Application Thread after the toolkit has started. */
    public ThemeManager() {
        this.platformPreferences = loadPlatformPreferences();
        if (platformPreferences != null) {
            platformPreferences.colorSchemeProperty().addListener((observable, oldScheme, newScheme) -> {
                if (preference.get() == ThemePreference.SYSTEM) {
                    updateAppliedTheme();
                }
            });
        }
        ObservableList<Window> windows = Window.getWindows();
        windows.addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                for (Window window : change.getAddedSubList()) {
                    watchWindow(window);
                }
            }
        });
        windows.forEach(this::watchWindow);
        appliedTheme.addListener((observable, oldTheme, newTheme) -> Window.getWindows().forEach(this::styleWindow));
        updateAppliedTheme();
    }

    public ReadOnlyObjectProperty<ThemePreference> preferenceProperty() {
        return preference.getReadOnlyProperty();
    }

    public ThemePreference getPreference() {
        return preference.get();
    }

    public ReadOnlyObjectProperty<AppliedTheme> appliedThemeProperty() {
        return appliedTheme.getReadOnlyProperty();
    }

    public AppliedTheme getAppliedTheme() {
        return appliedTheme.get();
    }

    /** Applies a preference immediately to every open window. */
    public void setPreference(ThemePreference newPreference) {
        preference.set(Objects.requireNonNull(newPreference));
        updateAppliedTheme();
    }

    /** True when JavaFX can report the operating system's light/dark setting. */
    public boolean isSystemDetectionAvailable() {
        return platformPreferences != null;
    }

    /** The operating system's current setting, if it can be detected. */
    public Optional<AppliedTheme> detectedSystemTheme() {
        if (platformPreferences == null) {
            return Optional.empty();
        }
        return Optional.of(platformPreferences.getColorScheme() == ColorScheme.DARK
                ? AppliedTheme.DARK : AppliedTheme.LIGHT);
    }

    /** Applies the shared and palette stylesheets to a scene. Called automatically for every window. */
    public void styleScene(Scene scene) {
        String activePalette = appliedTheme.get() == AppliedTheme.DARK ? DARK_PALETTE : LIGHT_PALETTE;
        ObservableList<String> stylesheets = scene.getStylesheets();
        if (stylesheets.contains(activePalette) && stylesheets.contains(COMPONENTS_STYLESHEET)) {
            return;
        }
        stylesheets.removeAll(LIGHT_PALETTE, DARK_PALETTE, COMPONENTS_STYLESHEET);
        stylesheets.addAll(COMPONENTS_STYLESHEET, activePalette);
    }

    private void updateAppliedTheme() {
        AppliedTheme resolved = switch (preference.get()) {
            case LIGHT -> AppliedTheme.LIGHT;
            case DARK -> AppliedTheme.DARK;
            case SYSTEM -> detectedSystemTheme().orElse(SYSTEM_FALLBACK_THEME);
        };
        appliedTheme.set(resolved);
    }

    private void watchWindow(Window window) {
        styleWindow(window);
        window.sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene != null) {
                styleScene(newScene);
            }
        });
    }

    private void styleWindow(Window window) {
        if (window.getScene() != null) {
            styleScene(window.getScene());
        }
    }

    private static Platform.Preferences loadPlatformPreferences() {
        try {
            return Platform.getPreferences();
        } catch (RuntimeException e) {
            LOGGER.log(Level.WARNING, "The operating system's colour scheme can't be detected; "
                    + "System appearance will use the light theme", e);
            return null;
        }
    }

    private static String stylesheet(String name) {
        URL resource = ThemeManager.class.getResource("/com/inlearn/styles/" + name);
        if (resource == null) {
            throw new IllegalStateException("Missing stylesheet " + name);
        }
        return resource.toExternalForm();
    }
}
