package com.inlearn;

import com.inlearn.config.AppConfig;
import com.inlearn.config.LoggingSetup;
import com.inlearn.exception.InLearnException;
import com.inlearn.ui.support.AppWindow;
import com.inlearn.ui.support.ErrorPresenter;
import com.inlearn.ui.support.Notifier;
import com.inlearn.ui.support.TaskRunner;
import com.inlearn.ui.support.UiContext;
import com.inlearn.ui.support.UserSession;
import com.inlearn.ui.theme.LocalPreferences;
import com.inlearn.ui.theme.ThemeManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * JavaFX entry point. Run with {@code mvn javafx:run}, or {@code java -jar target/inlearn-1.0.0-all.jar}
 * after packaging (which starts through {@link Launcher}).
 */
public class InLearnApp extends Application {

    private static final Logger LOGGER = Logger.getLogger(InLearnApp.class.getName());
    private static final double INITIAL_WIDTH = 1280;
    private static final double INITIAL_HEIGHT = 820;
    private static final double MIN_WIDTH = 960;
    private static final double MIN_HEIGHT = 640;
    private static final double STARTUP_ERROR_CARD_WIDTH = 560;
    private static final double STARTUP_ERROR_WINDOW_WIDTH = 720;
    private static final double STARTUP_ERROR_WINDOW_HEIGHT = 420;
    private static final Path FALLBACK_LOG_DIRECTORY = Path.of("inlearn-data", "logs");

    private TaskRunner taskRunner;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        ThemeManager themeManager = new ThemeManager();
        LocalPreferences localPreferences = new LocalPreferences();
        themeManager.setPreference(localPreferences.signedOutTheme());

        AppConfig config;
        AppContext appContext;
        try {
            config = AppConfig.load();
            LoggingSetup.configure(config.loggingDirectory());
            appContext = AppContext.fromConfig(config);
        } catch (InLearnException e) {
            LoggingSetup.configure(FALLBACK_LOG_DIRECTORY);
            LOGGER.log(Level.SEVERE, "InLearn could not start", e);
            showStartupError(stage, e.getMessage());
            return;
        }
        LOGGER.info("Starting InLearn with configuration from " + config.sourceDescription());

        StackPane contentHolder = new StackPane();
        VBox toastLayer = new VBox();
        StackPane root = new StackPane(contentHolder, toastLayer);
        root.getStyleClass().add("app-root");

        Notifier notifier = new Notifier(toastLayer);
        ErrorPresenter errorPresenter = new ErrorPresenter(notifier);
        taskRunner = new TaskRunner(errorPresenter);
        UiContext ui = new UiContext(appContext, new UserSession(), themeManager, localPreferences, taskRunner,
                notifier, errorPresenter, getHostServices(), stage);
        AppWindow appWindow = new AppWindow(ui, contentHolder);
        ui.setAppWindow(appWindow);
        errorPresenter.setAccountUnavailableHandler(() -> appWindow.forceSignOut(
                "You've been signed out because your account is no longer active."));

        Scene scene = new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT);
        themeManager.styleScene(scene);
        stage.setScene(scene);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setOnCloseRequest(event -> {
            if (!appWindow.confirmClose()) {
                event.consume();
            }
        });
        appWindow.showSignIn(null);
        stage.show();
    }

    @Override
    public void stop() {
        if (taskRunner != null) {
            taskRunner.shutdown();
        }
        LOGGER.info("InLearn closed");
    }

    /** A minimal window explaining why InLearn could not start (usually missing configuration). */
    private void showStartupError(Stage stage, String message) {
        Label title = new Label("InLearn can't start yet");
        title.getStyleClass().add("page-title");
        Label details = new Label(message);
        details.setWrapText(true);
        details.getStyleClass().add("body-text");
        Label help = new Label("See the Setup section of README.md for step-by-step instructions.");
        help.getStyleClass().add("muted");
        help.setWrapText(true);
        Button closeButton = new Button("Close");
        closeButton.getStyleClass().add("primary");
        closeButton.setDefaultButton(true);
        closeButton.setOnAction(event -> Platform.exit());

        VBox card = new VBox(title, details, help, closeButton);
        card.getStyleClass().addAll("card", "startup-error");
        card.setMaxWidth(STARTUP_ERROR_CARD_WIDTH);
        StackPane root = new StackPane(card);
        root.getStyleClass().add("app-root");
        StackPane.setAlignment(card, Pos.CENTER);

        stage.setScene(new Scene(root, STARTUP_ERROR_WINDOW_WIDTH, STARTUP_ERROR_WINDOW_HEIGHT));
        stage.setTitle("InLearn");
        stage.show();
    }
}
