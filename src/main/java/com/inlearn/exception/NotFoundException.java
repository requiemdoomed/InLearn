package com.inlearn.exception;

/**
 * The requested record does not exist (it may have been removed by someone else).
 */
public class NotFoundException extends InLearnException {

    public NotFoundException(String userMessage) {
        super(userMessage);
    }
}
