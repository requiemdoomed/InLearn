package com.inlearn.exception;

/**
 * The action conflicts with existing data or a business rule, for example a duplicate course
 * code or a role change that would orphan academic records. The message explains how to
 * resolve the conflict.
 */
public class ConflictException extends InLearnException {

    public ConflictException(String userMessage) {
        super(userMessage);
    }

    public ConflictException(String userMessage, Throwable cause) {
        super(userMessage, cause);
    }
}
