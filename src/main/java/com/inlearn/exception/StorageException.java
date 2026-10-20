package com.inlearn.exception;

/**
 * A file could not be stored, found or copied in managed storage.
 */
public class StorageException extends InLearnException {

    public StorageException(String userMessage) {
        super(userMessage);
    }

    public StorageException(String userMessage, Throwable cause) {
        super(userMessage, cause);
    }
}
