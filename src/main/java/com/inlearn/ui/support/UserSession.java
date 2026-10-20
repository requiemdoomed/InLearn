package com.inlearn.ui.support;

import com.inlearn.model.CurrentUser;
import com.inlearn.model.User;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

/**
 * Who is signed in to this window. Cleared on sign-out.
 *
 * <p>This is only the interface's memory of the session. Services re-check the account in the
 * database for every protected action, so a stale session can never grant extra access.
 */
public class UserSession {

    private final ReadOnlyObjectWrapper<User> signedInUser = new ReadOnlyObjectWrapper<>();

    public void start(User user) {
        signedInUser.set(user);
    }

    public void clear() {
        signedInUser.set(null);
    }

    /** Replaces the cached account details, for example after the user changes their display name. */
    public void update(User user) {
        if (isSignedIn() && user.id() == signedInUser.get().id()) {
            signedInUser.set(user);
        }
    }

    public boolean isSignedIn() {
        return signedInUser.get() != null;
    }

    public User user() {
        User user = signedInUser.get();
        if (user == null) {
            throw new IllegalStateException("No user is signed in");
        }
        return user;
    }

    public CurrentUser currentUser() {
        return CurrentUser.from(user());
    }

    public ReadOnlyObjectProperty<User> signedInUserProperty() {
        return signedInUser.getReadOnlyProperty();
    }
}
