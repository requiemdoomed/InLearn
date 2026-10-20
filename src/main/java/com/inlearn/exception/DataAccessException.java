package com.inlearn.exception;

/**
 * An unexpected database error. The technical cause is logged; the user sees a plain message.
 */
public class DataAccessException extends InLearnException {

    public DataAccessException(String userMessage, Throwable cause) {
        super(userMessage, cause);
    }

    public DataAccessException(Throwable cause) {
        this("Something went wrong while talking to the database. Please try again.", cause);
    }
}
