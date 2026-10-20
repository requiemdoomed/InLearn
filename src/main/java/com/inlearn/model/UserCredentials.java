package com.inlearn.model;

/**
 * A user together with the stored bcrypt hash. Only used inside the authentication service.
 */
public record UserCredentials(User user, String passwordHash) {

    @Override
    public String toString() {
        // Never print the hash, even in debug output.
        return "UserCredentials[user=" + user.email() + "]";
    }
}
