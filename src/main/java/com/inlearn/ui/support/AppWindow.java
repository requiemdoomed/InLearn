package com.inlearn.ui.support;

import com.inlearn.model.User;
import com.inlearn.ui.auth.LoginController;
import com.inlearn.ui.shell.ShellController;
import javafx.scene.layout.StackPane;

import java.util.logging.Logger;

/**
 * Switches the main window between the sign-in screen and the signed-in application shell.
 */
public class AppWindow {

    private static final Logger LOGGER = Logger.getLogger(AppWindow.class.getName());

    private final UiContext ui;
    private final StackPane contentHolder;
    private ShellController shell;

    public AppWindow(UiContext ui, StackPane contentHolder) {
        this.ui = ui;
        this.contentHolder = contentHolder;
    }

    /** Shows the sign-in screen using this computer's signed-out appearance. */
    public void showSignIn(String message) {
        ui.session().clear();
        ui.setNavigator(null);
        shell = null;
        ui.themeManager().setPreference(ui.localPreferences().signedOutTheme());
        clearKeyboardShortcuts();
        ViewLoader.LoadedView<LoginController> login = ui.viewLoader().load("auth/login.fxml");
        contentHolder.getChildren().setAll(login.root());
        login.controller().showMessage(message);
        ui.stage().setTitle("InLearn — Sign in");
    }

    /** Opens the application for a user who has just signed in, restoring their saved appearance. */
    public void openFor(User user) {
        ui.session().start(user);
        ui.themeManager().setPreference(user.themePreference());
        clearKeyboardShortcuts();
        ViewLoader.LoadedView<ShellController> loadedShell = ui.viewLoader().load("shell/main-shell.fxml");
        shell = loadedShell.controller();
        ui.setNavigator(shell);
        contentHolder.getChildren().setAll(loadedShell.root());
        ui.stage().setTitle("InLearn — " + user.fullName());
        shell.showHome();
    }

    /**
     * Signs out after checking for unsaved changes.
     *
     * @return false if the user chose to stay to keep their changes
     */
    public boolean signOut() {
        if (shell != null && shell.hasUnsavedChanges() && !ui.dialogs().confirmDiscardChanges()) {
            return false;
        }
        long userId = ui.session().isSignedIn() ? ui.session().user().id() : -1;
        ui.notifier().clear();
        showSignIn("You've signed out.");
        LOGGER.info("User " + userId + " signed out");
        return true;
    }

    /** Ends the session immediately, for example when the account was deactivated by an administrator. */
    public void forceSignOut(String reason) {
        ui.notifier().clear();
        showSignIn(reason);
    }

    /** Each screen registers its own shortcuts; remove the previous screen's before switching. */
    private void clearKeyboardShortcuts() {
        if (ui.stage().getScene() != null) {
            ui.stage().getScene().getAccelerators().clear();
        }
    }

    /** Asked when the window is closing; returns false to keep it open. */
    public boolean confirmClose() {
        return shell == null || !shell.hasUnsavedChanges() || ui.dialogs().confirmDiscardChanges();
    }
}
