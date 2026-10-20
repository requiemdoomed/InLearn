package com.inlearn.exception;

/**
 * Optimistic-locking failure: the record changed after the user opened it.
 */
public class ConcurrentUpdateException extends ConflictException {

    public ConcurrentUpdateException(String recordDescription) {
        super("This " + recordDescription + " was changed by someone else while you were editing it. "
                + "Refresh to load the latest version, then make your changes again.");
    }
}
