package com.inlearn.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Configures java.util.logging to write technical errors to a rotating log file.
 *
 * <p>Logging rule for this project: log what failed and why, never user passwords or
 * password hashes. Services never pass credentials to a logger, and SQL parameters are not logged.
 */
public final class LoggingSetup {

    private static final String LOG_FILE_PATTERN = "inlearn-%g.log";
    private static final int MAX_LOG_FILE_BYTES = 2 * 1024 * 1024;
    private static final int LOG_FILE_COUNT = 5;
    private static final String LOG_FORMAT = "%1$tF %1$tT %4$-7s [%3$s] %5$s%6$s%n";

    private LoggingSetup() {
    }

    /**
     * Sends log records to the console and, when possible, to {@code inlearn-N.log} in the given
     * directory. If the directory cannot be created the application keeps running with console logging.
     */
    public static void configure(Path logDirectory) {
        System.setProperty("java.util.logging.SimpleFormatter.format", LOG_FORMAT);
        Logger rootLogger = Logger.getLogger("");
        for (Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }
        rootLogger.setLevel(Level.INFO);

        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new SimpleFormatter());
        consoleHandler.setLevel(Level.INFO);
        rootLogger.addHandler(consoleHandler);

        try {
            Files.createDirectories(logDirectory);
            String pattern = logDirectory.resolve(LOG_FILE_PATTERN).toString();
            FileHandler fileHandler = new FileHandler(pattern, MAX_LOG_FILE_BYTES, LOG_FILE_COUNT, true);
            fileHandler.setFormatter(new SimpleFormatter());
            fileHandler.setLevel(Level.INFO);
            rootLogger.addHandler(fileHandler);
        } catch (IOException | SecurityException e) {
            rootLogger.log(Level.WARNING, "Could not open the log file in " + logDirectory
                    + "; logging to the console only.", e);
        }
    }
}
