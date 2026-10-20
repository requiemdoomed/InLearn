package com.inlearn.service;

import com.inlearn.dao.Database;
import com.inlearn.dao.UserDao;
import com.inlearn.exception.ConcurrentUpdateException;
import com.inlearn.exception.ValidationException;
import com.inlearn.model.CurrentUser;
import com.inlearn.model.ThemePreference;
import com.inlearn.model.User;
import com.inlearn.util.InputValidator;

/**
 * Changes a signed-in user can make to their own profile. Password changes live in
 * {@link AuthService#changePassword}.
 */
public class ProfileService {

    private final Database database;
    private final UserDao userDao;
    private final AuthorizationService authorization;

    public ProfileService(Database database, UserDao userDao, AuthorizationService authorization) {
        this.database = database;
        this.userDao = userDao;
        this.authorization = authorization;
    }

    public User loadProfile(CurrentUser currentUser) {
        return database.read(connection -> authorization.requireActiveUser(connection, currentUser));
    }

    public User updateDisplayName(CurrentUser currentUser, String fullName, int expectedVersion) {
        String cleanedName = InputValidator.requireText(fullName, "Display name", InputValidator.MAX_NAME_LENGTH);
        return database.inTransaction(connection -> {
            User user = authorization.requireActiveUser(connection, currentUser);
            if (!userDao.updateDisplayName(connection, user.id(), cleanedName, expectedVersion)) {
                throw new ConcurrentUpdateException("profile");
            }
            return userDao.findById(connection, user.id()).orElseThrow();
        });
    }

    /**
     * Saves the user's appearance choice. Only the theme column is written, so this never
     * overwrites or conflicts with other profile changes.
     */
    public void saveThemePreference(CurrentUser currentUser, ThemePreference preference) {
        if (preference == null) {
            throw new ValidationException("Choose an appearance.");
        }
        database.read(connection -> {
            User user = authorization.requireActiveUser(connection, currentUser);
            userDao.updateThemePreference(connection, user.id(), preference);
            return null;
        });
    }
}
