package com.inlearn.ui.admin;

import com.inlearn.exception.ValidationException;
import com.inlearn.model.AccountStatus;
import com.inlearn.model.Role;
import com.inlearn.model.User;
import com.inlearn.service.UserService.AccountDetails;
import com.inlearn.ui.support.FormDialog;
import com.inlearn.ui.support.FormMessages;
import com.inlearn.ui.support.UiContext;
import com.inlearn.ui.support.UnsavedChangesTracker;
import com.inlearn.util.InputValidator;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

import java.util.Arrays;

/**
 * The create/edit user form shown in a dialog from User Management.
 */
public class UserFormController implements FormDialog.Form {

    private final UiContext ui;
    private final UnsavedChangesTracker changes = new UnsavedChangesTracker();
    private User editedUser;

    @FXML private TextField fullNameField;
    @FXML private TextField emailField;
    @FXML private ComboBox<Role> roleChoice;
    @FXML private Label statusLabel;
    @FXML private ComboBox<AccountStatus> statusChoice;
    @FXML private Label roleHint;
    @FXML private Label passwordHeading;
    @FXML private Label passwordHint;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label errorLabel;

    public UserFormController(UiContext ui) {
        this.ui = ui;
    }

    @FXML
    private void initialize() {
        roleChoice.getItems().setAll(Role.values());
        statusChoice.getItems().setAll(AccountStatus.values());
        roleChoice.valueProperty().addListener((observable, oldRole, newRole) -> updateRoleHint(newRole));
    }

    public void prepareForCreate() {
        editedUser = null;
        roleChoice.setValue(Role.STUDENT);
        statusChoice.setValue(AccountStatus.ACTIVE);
        setStatusVisible(false);
        passwordHeading.setText("Temporary password");
        passwordHint.setText("At least " + InputValidator.MIN_PASSWORD_LENGTH
                + " characters, including a letter and a number. Give it to the user privately; "
                + "they can change it under Profile & settings.");
        trackChanges();
    }

    public void prepareForEdit(User user) {
        editedUser = user;
        fullNameField.setText(user.fullName());
        emailField.setText(user.email());
        roleChoice.setValue(user.role());
        statusChoice.setValue(user.status());
        setStatusVisible(true);
        passwordHeading.setText("Reset password (optional)");
        passwordHint.setText("Leave both fields blank to keep the current password.");
        trackChanges();
    }

    @Override
    public boolean hasUnsavedChanges() {
        return changes.hasUnsavedChanges();
    }

    @Override
    public void save(Button saveButton, Runnable onSaved) {
        FormMessages.clear(errorLabel);
        AccountDetails details = new AccountDetails(emailField.getText(), fullNameField.getText(), roleChoice.getValue());
        char[] password = passwordField.getText().toCharArray();
        char[] confirmation = confirmPasswordField.getText().toCharArray();
        boolean resetPassword = editedUser != null && (password.length > 0 || confirmation.length > 0);
        AccountStatus status = statusChoice.getValue();

        ui.tasks().task(() -> {
                    try {
                        return saveAccount(details, status, password, confirmation, resetPassword);
                    } finally {
                        Arrays.fill(password, '\0');
                        Arrays.fill(confirmation, '\0');
                    }
                })
                .disabling(saveButton)
                .onSuccess(savedUser -> {
                    ui.notifier().success(editedUser == null
                            ? savedUser.fullName() + "'s account has been created."
                            : "Changes to " + savedUser.fullName() + "'s account have been saved."
                            + (resetPassword ? " Their password was reset." : ""));
                    onSaved.run();
                })
                .onFailure(error -> FormMessages.showError(errorLabel, error))
                .start();
    }

    /** Runs on a background thread, so it only uses values captured from the form beforehand. */
    private User saveAccount(AccountDetails details, AccountStatus status, char[] password, char[] confirmation,
                             boolean resetPassword) {
        if (editedUser == null) {
            if (!Arrays.equals(password, confirmation)) {
                throw new ValidationException("The passwords don't match.");
            }
            return ui.app().userService().createUser(ui.currentUser(), details, password);
        }
        if (resetPassword) {
            // Check the new password before saving anything, so nothing is half-saved.
            if (!Arrays.equals(password, confirmation)) {
                throw new ValidationException("The passwords don't match.");
            }
            InputValidator.validateNewPassword(password);
        }
        User updated = ui.app().userService().updateUser(ui.currentUser(), editedUser.id(), details, status,
                editedUser.version());
        if (resetPassword) {
            ui.app().userService().resetPassword(ui.currentUser(), editedUser.id(), password, confirmation);
        }
        return updated;
    }

    private void updateRoleHint(Role role) {
        if (role == null) {
            roleHint.setText("");
            return;
        }
        roleHint.setText(switch (role) {
            case ADMIN -> "Administrators manage users, courses and enrollments. They can't grade work.";
            case INSTRUCTOR -> "Instructors manage the courses assigned to them: materials, assignments, grading and announcements.";
            case STUDENT -> "Students see only the courses they're enrolled in and only their own work and grades.";
        });
    }

    private void setStatusVisible(boolean visible) {
        statusLabel.setVisible(visible);
        statusLabel.setManaged(visible);
        statusChoice.setVisible(visible);
        statusChoice.setManaged(visible);
    }

    private void trackChanges() {
        changes.track(fullNameField.textProperty(), emailField.textProperty(), roleChoice.valueProperty(),
                statusChoice.valueProperty(), passwordField.textProperty(), confirmPasswordField.textProperty());
    }
}
