package com.inlearn.ui.auth;

import com.inlearn.model.User;
import com.inlearn.ui.support.ErrorPresenter;
import com.inlearn.ui.support.UiContext;
import com.inlearn.ui.theme.ThemeToggle;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.VBox;

import java.util.Arrays;

/**
 * The sign-in screen.
 */
public class LoginController {

    private static final KeyCombination TOGGLE_THEME_SHORTCUT =
            new KeyCodeCombination(KeyCode.L, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN);

    private final UiContext ui;

    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private CheckBox rememberEmailCheck;
    @FXML private Button signInButton;
    @FXML private Button themeToggleButton;
    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label errorLabel;
    @FXML private Label messageLabel;
    @FXML private VBox connectionBanner;
    @FXML private Label connectionMessage;

    public LoginController(UiContext ui) {
        this.ui = ui;
    }

    @FXML
    private void initialize() {
        // The signed-out appearance belongs to this computer, not to any account.
        ThemeToggle.attach(themeToggleButton, ui.themeManager(), true,
                preference -> ui.localPreferences().saveSignedOutTheme(preference));
        // On shared lab computers people can leave this unticked so nothing is remembered.
        String rememberedEmail = ui.localPreferences().rememberedEmail();
        emailField.setText(rememberedEmail);
        rememberEmailCheck.setSelected(!rememberedEmail.isEmpty());
        emailField.sceneProperty().addListener((observable, oldScene, scene) -> {
            if (scene != null) {
                scene.getAccelerators().put(TOGGLE_THEME_SHORTCUT, themeToggleButton::fire);
                Platform.runLater(rememberedEmail.isEmpty() ? emailField::requestFocus : passwordField::requestFocus);
            }
        });
        progressIndicator.managedProperty().bind(progressIndicator.visibleProperty());
        emailField.textProperty().addListener((observable, oldText, newText) -> hideError());
        passwordField.textProperty().addListener((observable, oldText, newText) -> hideError());
        onCheckConnection();
    }

    /** Shows an informational message, for example after signing out. */
    public void showMessage(String message) {
        boolean hasMessage = message != null && !message.isBlank();
        messageLabel.setText(hasMessage ? message : "");
        messageLabel.setVisible(hasMessage);
        messageLabel.setManaged(hasMessage);
    }

    @FXML
    private void onSignIn() {
        if (signInButton.isDisabled()) {
            return;
        }
        String email = emailField.getText();
        char[] password = passwordField.getText().toCharArray();
        hideError();
        showMessage(null);
        ui.tasks().task(() -> {
                    try {
                        return ui.app().authService().signIn(email, password);
                    } finally {
                        Arrays.fill(password, '\0');
                    }
                })
                .disabling(signInButton, emailField, passwordField)
                .showing(progressIndicator)
                .onSuccess(this::openApplication)
                .onFailure(error -> {
                    passwordField.clear();
                    showError(ErrorPresenter.userMessage(error));
                    passwordField.requestFocus();
                })
                .start();
    }

    /** Checks the database in the background so set-up problems are explained before signing in. */
    @FXML
    private void onCheckConnection() {
        ui.tasks().task(() -> {
                    ui.app().database().checkConnection();
                    return Boolean.TRUE;
                })
                .onSuccess(reachable -> setConnectionBanner(null))
                .onFailure(error -> setConnectionBanner(ErrorPresenter.userMessage(error)))
                .start();
    }

    private void openApplication(User user) {
        passwordField.clear();
        ui.localPreferences().setRememberedEmail(rememberEmailCheck.isSelected() ? user.email() : null);
        ui.appWindow().openFor(user);
    }

    private void setConnectionBanner(String problem) {
        boolean hasProblem = problem != null;
        connectionMessage.setText(hasProblem ? problem : "");
        connectionBanner.setVisible(hasProblem);
        connectionBanner.setManaged(hasProblem);
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    private void hideError() {
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
    }
}
