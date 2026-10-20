package com.inlearn.exception;

/**
 * Input that cannot be saved, such as an empty title or a grade above the maximum.
 */
public class ValidationException extends InLearnException {

    public ValidationException(String userMessage) {
        super(userMessage);
    }
}
