package com.inlearn.ui.common;

import com.inlearn.model.ThemePreference;
import com.inlearn.model.User;
import com.inlearn.ui.support.FormMessages;
import com.inlearn.ui.support.Page;
import com.inlearn.ui.support.UiContext;
import com.inlearn.ui.support.UnsavedChangesTracker;
import com.inlearn.ui.theme.ThemeManager;
import com.inlearn.util.InputValidator;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.WeakChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleGroup;

import java.util.Arrays;
import java.util.Map;

/**
 * Profile and settings: display name, appearance and password.
 */
public class ProfileController implements Page {

    private final UiContext ui;
    private final UnsavedChangesTracker nameChanges = new UnsavedChangesTracker();
    private final UnsavedChangesTracker passwordChanges = new UnsavedChangesTracker();
    private User profile;
    private Map<ThemePreference, RadioButton> themeOptions;
    private boolean updatingThemeSelection;
    private ChangeListener<ThemePreference> preferenceListener;
    private ChangeListener<ThemeManager.AppliedTheme> appliedThemeListener;

    @FXML private TextField displayNameField;
    @FXML private Label emailValue;
    @FXML private Label roleValue;
    @FXML private Label profileMessage;
    @FXML private Button saveNameButton;
    @FXML private ToggleGroup themeGroup;
    @FXML private RadioButton systemThemeOption;
    @FXML private RadioButton lightThemeOption;
    @FXML private RadioButton darkThemeOption;
    @FXML private Label systemThemeNote;
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label passwordRules;
    @FXML private Label passwordMessage;
    @FXML private Button changePasswordButton;

    public ProfileController(UiContext ui) {
        this.ui = ui;
    }

    @FXML
    private void initialize() {
        themeOptions = Map.of(
                ThemePreference.SYSTEM, systemThemeOption,
                ThemePreference.LIGHT, lightThemeOption,
                ThemePreference.DARK, darkThemeOption);
        passwordRules.setText("At least " + InputValidator.MIN_PASSWORD_LENGTH + " characters, including a letter and a number.");
        describeSystemDetection();

        // Reflect theme changes made elsewhere (for example the header toggle) without saving twice.
        // Weak listeners let this page be garbage-collected after the user navigates away.
        preferenceListener = (observable, oldPreference, newPreference) -> selectThemeOption(newPreference);
        appliedThemeListener = (observable, oldTheme, newTheme) -> describeSystemDetection();
        ui.themeManager().preferenceProperty().addListener(new WeakChangeListener<>(preferenceListener));
        ui.themeManager().appliedThemeProperty().addListener(new WeakChangeListener<>(appliedThemeListener));
        selectThemeOption(ui.themeManager().getPreference());
        themeGroup.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> onThemeChosen(newToggle));

        nameChanges.track(displayNameField.textProperty());
        passwordChanges.track(currentPasswordField.textProperty(), newPasswordField.textProperty(),
                confirmPasswordField.textProperty());
    }

    @Override
    public void onShown() {
        refresh();
    }

    @Override
    public void refresh() {
        ui.tasks().task(() -> ui.app().profileService().loadProfile(ui.currentUser()))
                .onSuccess(this::showProfile)
                .start();
    }

    @Override
    public boolean hasUnsavedChanges() {
        return nameChanges.hasUnsavedChanges() || passwordChanges.hasUnsavedChanges();
    }

    @Override
    public void save() {
        if (nameChanges.hasUnsavedChanges() && !saveNameButton.isDisabled()) {
            onSaveName();
        }
    }

    private void showProfile(User loaded) {
        boolean keepTypedName = nameChanges.hasUnsavedChanges();
        this.profile = loaded;
        if (!keepTypedName) {
            displayNameField.setText(loaded.fullName());
            nameChanges.markSaved();
        }
        emailValue.setText(loaded.email());
        roleValue.setText(loaded.role().displayName());
    }

    @FXML
    private void onSaveName() {
        if (profile == null) {
            return;
        }
        FormMessages.clear(profileMessage);
        String newName = displayNameField.getText();
        ui.tasks().task(() -> ui.app().profileService().updateDisplayName(ui.currentUser(), newName, profile.version()))
                .disabling(saveNameButton)
                .onSuccess(updated -> {
                    profile = updated;
                    displayNameField.setText(updated.fullName());
                    nameChanges.markSaved();
                    ui.session().update(updated);
                    FormMessages.showSuccess(profileMessage, "Your display name has been updated.");
                })
                .onFailure(error -> FormMessages.showError(profileMessage, error))
                .start();
    }

    @FXML
    private void onChangePassword() {
        FormMessages.clear(passwordMessage);
        char[] current = currentPasswordField.getText().toCharArray();
        char[] newPassword = newPasswordField.getText().toCharArray();
        char[] confirmation = confirmPasswordField.getText().toCharArray();
        ui.tasks().task(() -> {
                    try {
                        ui.app().authService().changePassword(ui.currentUser(), current, newPassword, confirmation);
                        return Boolean.TRUE;
                    } finally {
                        Arrays.fill(current, '\0');
                        Arrays.fill(newPassword, '\0');
                        Arrays.fill(confirmation, '\0');
                    }
                })
                .disabling(changePasswordButton)
                .onSuccess(changed -> {
                    currentPasswordField.clear();
                    newPasswordField.clear();
                    confirmPasswordField.clear();
                    passwordChanges.markSaved();
                    FormMessages.showSuccess(passwordMessage, "Your password has been changed. Use it the next time you sign in.");
                })
                .onFailure(error -> FormMessages.showError(passwordMessage, error))
                .start();
    }

    /** Applies the chosen appearance immediately and saves only the theme column. */
    private void onThemeChosen(Toggle toggle) {
        if (updatingThemeSelection || toggle == null) {
            return;
        }
        ThemePreference chosen = themeOptions.entrySet().stream()
                .filter(entry -> entry.getValue() == toggle)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(ThemePreference.SYSTEM);
        if (chosen == ui.themeManager().getPreference()) {
            return;
        }
        ui.themeManager().setPreference(chosen);
        ui.tasks().task(() -> {
                    ui.app().profileService().saveThemePreference(ui.currentUser(), chosen);
                    return chosen;
                })
                .onSuccess(saved -> ui.notifier().success("Appearance set to " + saved.displayName() + "."))
                .start();
    }

    private void selectThemeOption(ThemePreference preference) {
        updatingThemeSelection = true;
        try {
            themeGroup.selectToggle(themeOptions.get(preference));
        } finally {
            updatingThemeSelection = false;
        }
    }

    private void describeSystemDetection() {
        ThemeManager themeManager = ui.themeManager();
        systemThemeNote.setText(themeManager.detectedSystemTheme()
                .map(theme -> "System follows your operating system's setting, which is currently "
                        + (theme == ThemeManager.AppliedTheme.DARK ? "dark" : "light") + ".")
                .orElse("Your operating system's appearance can't be detected here, so System uses the light theme."));
    }
}
