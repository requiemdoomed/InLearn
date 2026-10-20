package com.inlearn.ui.theme;

import com.inlearn.model.ThemePreference;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;

import java.util.function.Consumer;

/**
 * Wires a button as the quick light/dark switch used in the header and on the sign-in screen.
 * The button always describes what it will do ("Dark mode" while the light theme is showing),
 * and the label updates whenever the theme changes from anywhere.
 */
public final class ThemeToggle {

    private static final String LISTENER_KEY = "inlearn.themeToggleListener";

    private ThemeToggle() {
    }

    /**
     * @param showText      whether to show a text label next to the icon
     * @param onPreference  called with the newly chosen preference so the caller can persist it
     */
    public static void attach(Button button, ThemeManager themeManager, boolean showText,
                              Consumer<ThemePreference> onPreference) {
        Region icon = new Region();
        icon.getStyleClass().add("nav-icon");
        button.setGraphic(icon);
        button.getStyleClass().add("icon-button");

        Runnable updateAppearance = () -> {
            boolean darkShowing = themeManager.getAppliedTheme() == ThemeManager.AppliedTheme.DARK;
            String action = darkShowing ? "Switch to light mode" : "Switch to dark mode";
            icon.getStyleClass().removeAll("icon-sun", "icon-moon");
            icon.getStyleClass().add(darkShowing ? "icon-sun" : "icon-moon");
            button.setText(showText ? (darkShowing ? "Light mode" : "Dark mode") : null);
            button.setAccessibleText(action);
            button.setTooltip(new Tooltip(action + " (Ctrl+Shift+L)"));
        };
        // The button keeps the listener alive; the weak wrapper lets a discarded screen be garbage-collected.
        ChangeListener<ThemeManager.AppliedTheme> listener = (observable, oldTheme, newTheme) -> updateAppearance.run();
        button.getProperties().put(LISTENER_KEY, listener);
        themeManager.appliedThemeProperty().addListener(new WeakChangeListener<>(listener));
        updateAppearance.run();

        button.setOnAction(event -> {
            ThemePreference next = themeManager.getAppliedTheme() == ThemeManager.AppliedTheme.DARK
                    ? ThemePreference.LIGHT : ThemePreference.DARK;
            themeManager.setPreference(next);
            onPreference.accept(next);
        });
    }
}
