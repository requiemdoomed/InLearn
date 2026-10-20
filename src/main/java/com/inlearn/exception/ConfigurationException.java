package com.inlearn.exception;

/**
 * The application configuration is missing or invalid.
 */
public class ConfigurationException extends InLearnException {

    public ConfigurationException(String userMessage) {
        super(userMessage);
    }

    public ConfigurationException(String userMessage, Throwable cause) {
        super(userMessage, cause);
    }
}
