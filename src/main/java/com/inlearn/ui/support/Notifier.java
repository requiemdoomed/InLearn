package com.inlearn.ui.support;

import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/**
 * Shows short success, information and error messages ("toasts") in the top-right corner.
 * Error toasts can carry a recovery action such as "Try again".
 */
public class Notifier {

    private static final Duration SUCCESS_DISPLAY_TIME = Duration.seconds(4);
    private static final Duration ERROR_DISPLAY_TIME = Duration.seconds(12);
    private static final int MAX_VISIBLE_TOASTS = 4;

    /** Visual tone; the toast text also states the outcome so colour is never the only cue. */
    private enum Tone {
        SUCCESS("toast-success", "Done"),
        INFO("toast-info", "Note"),
        ERROR("toast-error", "Problem");

        private final String styleClass;
        private final String heading;

        Tone(String styleClass, String heading) {
            this.styleClass = styleClass;
            this.heading = heading;
        }
    }

    private final VBox toastContainer;

    /** @param toastContainer the overlay that holds toasts; it sits above all page content */
    public Notifier(VBox toastContainer) {
        this.toastContainer = toastContainer;
        toastContainer.setAlignment(Pos.TOP_RIGHT);
        toastContainer.setPickOnBounds(false);
        toastContainer.getStyleClass().add("toast-container");
    }

    public void success(String message) {
        show(Tone.SUCCESS, message, null, null);
    }

    public void info(String message) {
        show(Tone.INFO, message, null, null);
    }

    public void error(String message) {
        show(Tone.ERROR, message, null, null);
    }

    /** An error with a recovery button, for example "Try again" after a connection failure. */
    public void error(String message, String actionText, Runnable action) {
        show(Tone.ERROR, message, actionText, action);
    }

    public void clear() {
        toastContainer.getChildren().clear();
    }

    private void show(Tone tone, String message, String actionText, Runnable action) {
        HBox toast = new HBox();
        toast.getStyleClass().addAll("toast", tone.styleClass);
        toast.setAccessibleRole(AccessibleRole.TEXT);
        toast.setAccessibleText(tone.heading + ": " + message);

        Label heading = new Label(tone.heading);
        heading.getStyleClass().add("toast-heading");
        Label text = new Label(message);
        text.setWrapText(true);
        text.getStyleClass().add("toast-message");
        VBox textBox = new VBox(heading, text);
        textBox.getStyleClass().add("toast-text");
        HBox.setHgrow(textBox, Priority.ALWAYS);
        toast.getChildren().add(textBox);

        if (action != null) {
            Button actionButton = new Button(actionText);
            actionButton.getStyleClass().add("toast-action");
            actionButton.setOnAction(event -> {
                dismiss(toast);
                action.run();
            });
            toast.getChildren().add(actionButton);
        }

        Button closeButton = new Button("✕");
        closeButton.getStyleClass().add("toast-close");
        closeButton.setAccessibleText("Dismiss message");
        closeButton.setOnAction(event -> dismiss(toast));
        toast.getChildren().add(closeButton);

        if (toastContainer.getChildren().size() >= MAX_VISIBLE_TOASTS) {
            toastContainer.getChildren().removeFirst();
        }
        toastContainer.getChildren().add(toast);

        PauseTransition timer = new PauseTransition(tone == Tone.ERROR ? ERROR_DISPLAY_TIME : SUCCESS_DISPLAY_TIME);
        timer.setOnFinished(event -> dismiss(toast));
        // Keep the message on screen while the pointer is over it, so it can be read in full.
        toast.hoverProperty().addListener((observable, wasHovered, isHovered) -> {
            if (isHovered) {
                timer.pause();
            } else {
                timer.playFromStart();
            }
        });
        timer.play();
    }

    private void dismiss(HBox toast) {
        toastContainer.getChildren().remove(toast);
    }
}
