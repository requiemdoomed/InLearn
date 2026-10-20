package com.inlearn.exception;

/**
 * The signed-in account was deactivated (or removed) while the session was open.
 * The interface responds by signing the user out.
 */
public class AccountUnavailableException extends AccessDeniedException {

    public AccountUnavailableException() {
        super("Your account is no longer active. Please contact your administrator.");
    }
}
