package com.inlearn.ui.support;

import com.inlearn.AppContext;
import com.inlearn.model.CurrentUser;
import com.inlearn.ui.theme.LocalPreferences;
import com.inlearn.ui.theme.ThemeManager;
import javafx.application.HostServices;
import javafx.stage.Stage;

/**
 * Everything a screen controller needs: the services, the signed-in user, and shared
 * interface helpers. Controllers receive it through their constructor (see {@link ViewLoader}).
 */
public final class UiContext {

    private final AppContext app;
    private final UserSession session;
    private final ThemeManager themeManager;
    private final LocalPreferences localPreferences;
    private final TaskRunner taskRunner;
    private final Notifier notifier;
    private final ErrorPresenter errorPresenter;
    private final Dialogs dialogs;
    private final UiFormats formats;
    private final HostServices hostServices;
    private final Stage stage;
    private final ViewLoader viewLoader;
    private Navigator navigator;
    private AppWindow appWindow;

    public UiContext(AppContext app, UserSession session, ThemeManager themeManager, LocalPreferences localPreferences,
                     TaskRunner taskRunner, Notifier notifier, ErrorPresenter errorPresenter, HostServices hostServices,
                     Stage stage) {
        this.app = app;
        this.session = session;
        this.themeManager = themeManager;
        this.localPreferences = localPreferences;
        this.taskRunner = taskRunner;
        this.notifier = notifier;
        this.errorPresenter = errorPresenter;
        this.hostServices = hostServices;
        this.stage = stage;
        this.dialogs = new Dialogs(stage);
        this.formats = new UiFormats(app.displayZone());
        this.viewLoader = new ViewLoader(this);
    }

    public AppContext app() {
        return app;
    }

    public UserSession session() {
        return session;
    }

    /** Shortcut for the signed-in user's identity, passed to every service call. */
    public CurrentUser currentUser() {
        return session.currentUser();
    }

    public ThemeManager themeManager() {
        return themeManager;
    }

    public LocalPreferences localPreferences() {
        return localPreferences;
    }

    public TaskRunner tasks() {
        return taskRunner;
    }

    public Notifier notifier() {
        return notifier;
    }

    public ErrorPresenter errorPresenter() {
        return errorPresenter;
    }

    public Dialogs dialogs() {
        return dialogs;
    }

    public UiFormats formats() {
        return formats;
    }

    public HostServices hostServices() {
        return hostServices;
    }

    public Stage stage() {
        return stage;
    }

    public ViewLoader viewLoader() {
        return viewLoader;
    }

    public Navigator navigator() {
        if (navigator == null) {
            throw new IllegalStateException("Navigation is only available after signing in");
        }
        return navigator;
    }

    public void setNavigator(Navigator navigator) {
        this.navigator = navigator;
    }

    public AppWindow appWindow() {
        return appWindow;
    }

    public void setAppWindow(AppWindow appWindow) {
        this.appWindow = appWindow;
    }
}
