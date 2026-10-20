package com.inlearn.ui.support;

import com.inlearn.exception.AccountUnavailableException;
import com.inlearn.exception.DatabaseUnavailableException;
import com.inlearn.exception.InLearnException;
import com.inlearn.exception.StorageException;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Turns exceptions from background work into messages for the user.
 *
 * <p>Expected problems ({@link InLearnException}) already carry a user-friendly message.
 * Anything else is a bug: it is logged with its stack trace and the user sees a short,
 * non-technical message instead of SQL or exception details.
 */
public class ErrorPresenter {

    public static final String UNEXPECTED_ERROR_MESSAGE =
            "Something unexpected went wrong. The details were saved to InLearn's log file.";

    private static final Logger LOGGER = Logger.getLogger(ErrorPresenter.class.getName());
    private static final String RETRY_LABEL = "Try again";

    private final Notifier notifier;
    private Runnable accountUnavailableHandler = () -> {
    };

    public ErrorPresenter(Notifier notifier) {
        this.notifier = notifier;
    }

    /** What to do when the signed-in account has been deactivated (normally: sign out). */
    public void setAccountUnavailableHandler(Runnable handler) {
        this.accountUnavailableHandler = handler;
    }

    /**
     * Shows an error as a notification.
     *
     * @param retry optional action offered as "Try again" for connection and storage failures
     */
    public void present(Throwable error, Runnable retry) {
        Throwable cause = unwrap(error);
        if (cause instanceof AccountUnavailableException) {
            notifier.error(cause.getMessage());
            accountUnavailableHandler.run();
            return;
        }
        boolean recoverable = cause instanceof DatabaseUnavailableException || cause instanceof StorageException;
        if (recoverable && retry != null) {
            notifier.error(cause.getMessage(), RETRY_LABEL, retry);
            return;
        }
        notifier.error(userMessage(cause));
    }

    /** The message to show for an error, for example in a form's inline error label. */
    public static String userMessage(Throwable error) {
        Throwable cause = unwrap(error);
        if (cause instanceof InLearnException) {
            return cause.getMessage();
        }
        LOGGER.log(Level.SEVERE, "Unexpected error", cause);
        return UNEXPECTED_ERROR_MESSAGE;
    }

    /** True when the error should end the session. */
    public static boolean isAccountUnavailable(Throwable error) {
        return unwrap(error) instanceof AccountUnavailableException;
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof ExecutionException || current instanceof CompletionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
