package com.inlearn.model;

/**
 * Identifies who is performing an action. Services never trust the role stored here for
 * protected actions: they reload the account from the database to confirm it is still active
 * and still has the expected role.
 */
public record CurrentUser(long id, String email, String fullName, Role role) {

    public static CurrentUser from(User user) {
        return new CurrentUser(user.id(), user.email(), user.fullName(), user.role());
    }
}
