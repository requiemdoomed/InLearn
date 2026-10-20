package com.inlearn.ui.support;

import javafx.application.Platform;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;

/**
 * Shows and hides the inline message label that sits under a form's fields. Hidden labels are
 * also unmanaged so they take no space.
 */
public final class FormMessages {

    private FormMessages() {
    }

    public static void showError(Label label, String message) {
        show(label, message, "form-error");
    }

    public static void showError(Label label, Throwable error) {
        showError(label, ErrorPresenter.userMessage(error));
    }

    public static void showSuccess(Label label, String message) {
        show(label, message, "form-success");
    }

    public static void clear(Label label) {
        label.setText("");
        label.setVisible(false);
        label.setManaged(false);
    }

    private static void show(Label label, String message, String styleClass) {
        label.getStyleClass().removeAll("form-error", "form-success");
        label.getStyleClass().add(styleClass);
        label.setText(message);
        label.setWrapText(true);
        label.setVisible(true);
        label.setManaged(true);
        growDialogToFit(label);
    }

    /** Dialog windows don't grow on their own when a message appears, so make room for it. */
    private static void growDialogToFit(Label label) {
        Platform.runLater(() -> {
            if (label.getScene() != null && label.getScene().getRoot() instanceof DialogPane pane) {
                FormDialog.growToFitContent(pane);
            }
        });
    }
}
