package com.inlearn.exception;

/**
 * Base class for errors the application expects and can explain. The message of every
 * subclass is written for end users: it is shown in the interface as-is, so it must never
 * contain SQL, stack details or credentials.
 */
public class InLearnException extends RuntimeException {

    public InLearnException(String userMessage) {
        super(userMessage);
    }

    public InLearnException(String userMessage, Throwable cause) {
        super(userMessage, cause);
    }
}
