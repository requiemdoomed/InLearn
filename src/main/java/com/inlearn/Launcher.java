package com.inlearn;

/**
 * Main class for the packaged jar ({@code java -jar target/inlearn-1.0.0-all.jar}).
 *
 * <p>When a jar's main class extends {@link javafx.application.Application}, the Java launcher
 * insists that JavaFX be on the module path. Starting through this plain class lets the
 * single runnable jar work with JavaFX on the class path.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        InLearnApp.main(args);
    }
}
