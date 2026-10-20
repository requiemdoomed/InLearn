package com.inlearn.exception;

/**
 * The database server could not be reached. The interface offers a "Try again" action.
 */
public class DatabaseUnavailableException extends DataAccessException {

    public DatabaseUnavailableException(Throwable cause) {
        super("InLearn can't reach the database right now. Check that the MySQL server is running "
                + "and that you're connected to the network, then try again.", cause);
    }
}
