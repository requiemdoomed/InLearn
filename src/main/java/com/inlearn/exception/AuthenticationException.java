package com.inlearn.exception;

/**
 * Sign-in failed. For wrong emails and wrong passwords the message is deliberately identical so
 * it never reveals which email addresses have accounts.
 */
public class AuthenticationException extends InLearnException {

    public static final String INVALID_CREDENTIALS_MESSAGE = "The email or password you entered is incorrect.";

    public AuthenticationException(String userMessage) {
        super(userMessage);
    }

    public static AuthenticationException invalidCredentials() {
        return new AuthenticationException(INVALID_CREDENTIALS_MESSAGE);
    }
}
