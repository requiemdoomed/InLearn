package com.inlearn.ui.support;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.stage.Window;

import java.util.Optional;

/**
 * Consistent confirmation and message dialogs. Dialog windows are themed automatically by
 * {@link com.inlearn.ui.theme.ThemeManager}.
 */
public class Dialogs {

    private static final double DIALOG_MIN_WIDTH = 420;
    private static final double DIALOG_PREFERRED_WIDTH = 460;

    private final Window owner;

    public Dialogs(Window owner) {
        this.owner = owner;
    }

    /**
     * Asks the user to confirm an action.
     *
     * @param confirmLabel text for the confirm button, such as "Archive course"
     * @param destructive  styles the confirm button as dangerous and makes Cancel the default
     * @return true if the user confirmed
     */
    public boolean confirm(String title, String message, String confirmLabel, boolean destructive) {
        ButtonType confirmButton = new ButtonType(confirmLabel, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(destructive ? Alert.AlertType.WARNING : Alert.AlertType.CONFIRMATION,
                message, confirmButton, cancelButton);
        prepare(alert, title);
        Button confirm = (Button) alert.getDialogPane().lookupButton(confirmButton);
        Button cancel = (Button) alert.getDialogPane().lookupButton(cancelButton);
        if (destructive) {
            confirm.getStyleClass().add("danger");
            confirm.setDefaultButton(false);
            cancel.setDefaultButton(true);
        } else {
            confirm.getStyleClass().add("primary");
        }
        Optional<ButtonType> choice = alert.showAndWait();
        return choice.isPresent() && choice.get() == confirmButton;
    }

    /** Asks whether to discard unsaved changes. */
    public boolean confirmDiscardChanges() {
        return confirm("Unsaved changes", "You have changes that haven't been saved. Leave without saving?",
                "Discard changes", true);
    }

    public void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        prepare(alert, title);
        alert.showAndWait();
    }

    public void showInformation(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        prepare(alert, title);
        alert.showAndWait();
    }

    public Window owner() {
        return owner;
    }

    private void prepare(Alert alert, String title) {
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(title);
        DialogPane pane = alert.getDialogPane();
        pane.getStyleClass().add("inlearn-dialog");
        pane.setMinWidth(DIALOG_MIN_WIDTH);
        pane.setPrefWidth(DIALOG_PREFERRED_WIDTH);
    }
}
