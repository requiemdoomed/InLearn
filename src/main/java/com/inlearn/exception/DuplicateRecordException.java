package com.inlearn.exception;

/**
 * The database rejected a row because it would break a unique constraint. Services usually
 * check for duplicates first and give a specific message; this covers the race where two
 * people save the same value at the same moment.
 */
public class DuplicateRecordException extends ConflictException {

    public DuplicateRecordException(Throwable cause) {
        super("That record already exists. Refresh the page to see the latest data.", cause);
    }
}
