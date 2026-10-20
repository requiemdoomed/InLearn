package com.inlearn.exception;

/**
 * The signed-in user is not allowed to perform the action or see the resource.
 */
public class AccessDeniedException extends InLearnException {

    public AccessDeniedException(String userMessage) {
        super(userMessage);
    }
}
