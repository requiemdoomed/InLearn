package com.inlearn.ui.support;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.stage.Window;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A modal dialog that hosts a form. The Save button runs the form's save in the background and
 * only closes the dialog after it succeeds, so validation errors appear inside the dialog
 * without losing what the user typed. Cancel and the window's close button ask before
 * discarding unsaved changes.
 */
public final class FormDialog {

    private static final double FORM_MIN_WIDTH = 520;

    /** Implemented by controllers of forms shown in a {@link FormDialog}. */
    public interface Form {

        /** True if the user changed anything since the form was opened. */
        boolean hasUnsavedChanges();

        /**
         * Validates and saves in the background.
         *
         * @param saveButton disable this while saving to prevent double submission
         * @param onSaved    call on the JavaFX thread after a successful save to close the dialog
         */
        void save(Button saveButton, Runnable onSaved);
    }

    private FormDialog() {
    }

    /**
     * Shows the dialog and waits until it is closed.
     *
     * @return true if the form was saved, false if it was cancelled
     */
    public static boolean show(UiContext ui, String title, String saveLabel, Parent content, Form form) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(ui.stage());
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.setResizable(true);

        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("inlearn-dialog");
        pane.setContent(content);
        pane.setMinWidth(FORM_MIN_WIDTH);

        ButtonType saveType = new ButtonType(saveLabel, ButtonBar.ButtonData.OK_DONE);
        pane.getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        Button saveButton = (Button) pane.lookupButton(saveType);
        saveButton.getStyleClass().add("primary");
        Button cancelButton = (Button) pane.lookupButton(ButtonType.CANCEL);

        // A lambda can't assign a local variable, so the flag lives in an AtomicBoolean.
        AtomicBoolean saved = new AtomicBoolean(false);
        saveButton.addEventFilter(ActionEvent.ACTION, event -> {
            // Keep the dialog open until the background save has succeeded.
            event.consume();
            form.save(saveButton, () -> {
                saved.set(true);
                dialog.setResult(saveType);
                dialog.close();
            });
        });
        cancelButton.addEventFilter(ActionEvent.ACTION, event -> {
            if (form.hasUnsavedChanges() && !ui.dialogs().confirmDiscardChanges()) {
                event.consume();
            }
        });
        dialog.setOnCloseRequest(event -> {
            if (!saved.get() && dialog.getResult() != ButtonType.CANCEL && form.hasUnsavedChanges()
                    && !ui.dialogs().confirmDiscardChanges()) {
                event.consume();
            }
        });

        // Start typing in the first field rather than on the Save button.
        dialog.setOnShown(event -> Platform.runLater(() -> {
            growToFitContent(pane);
            Node firstInput = content.lookup(".text-input");
            if (firstInput != null) {
                firstInput.requestFocus();
            }
        }));
        dialog.showAndWait();
        return saved.get();
    }

    /**
     * Makes a dialog tall enough for its content at the dialog's current width. JavaFX sizes
     * dialogs before wrapped text is measured, so long hints or error messages could otherwise
     * be cut off.
     */
    static void growToFitContent(DialogPane pane) {
        Node content = pane.getContent();
        Window window = pane.getScene() == null ? null : pane.getScene().getWindow();
        if (content == null || window == null) {
            return;
        }
        // Wrapped labels must never be squeezed to a single truncated line.
        for (Node node : content.lookupAll(".label")) {
            if (node instanceof Label label && label.isWrapText()) {
                label.setMinHeight(Region.USE_PREF_SIZE);
            }
        }
        content.applyCss();
        if (content instanceof Parent parent) {
            parent.layout();
        }
        double availableHeight = content.getLayoutBounds().getHeight();
        double neededHeight = content.prefHeight(content.getLayoutBounds().getWidth());
        if (neededHeight > availableHeight) {
            window.setHeight(window.getHeight() + (neededHeight - availableHeight));
        }
    }
}
