package com.inlearn.service;

import com.inlearn.dao.Database;
import com.inlearn.dao.UserDao;
import com.inlearn.exception.AuthenticationException;
import com.inlearn.exception.ValidationException;
import com.inlearn.model.CurrentUser;
import com.inlearn.model.User;
import com.inlearn.model.UserCredentials;
import com.inlearn.util.InputValidator;
import com.inlearn.util.PasswordHasher;

import java.time.Clock;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Signs users in and changes passwords.
 */
public class AuthService {

    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());

    private final Database database;
    private final UserDao userDao;
    private final AuthorizationService authorization;
    private final PasswordHasher passwordHasher;
    private final Clock clock;
    private final SignInThrottle signInThrottle;

    /**
     * A real bcrypt hash of a random value, checked when an email is unknown so a failed sign-in
     * takes the same time whether or not the account exists.
     */
    private final String timingEqualizerHash;

    public AuthService(Database database, UserDao userDao, AuthorizationService authorization,
                       PasswordHasher passwordHasher, Clock clock) {
        this.database = database;
        this.userDao = userDao;
        this.authorization = authorization;
        this.passwordHasher = passwordHasher;
        this.clock = clock;
        this.signInThrottle = new SignInThrottle(clock);
        this.timingEqualizerHash = passwordHasher.hash(UUID.randomUUID().toString().toCharArray());
    }

    /**
     * Verifies an email and password.
     *
     * @return the signed-in account
     * @throws AuthenticationException with a generic message for any wrong email or password,
     *                                 a specific message for a correct password on an inactive account,
     *                                 or a lockout message after too many failed attempts
     */
    public User signIn(String email, char[] password) {
        if (email == null || email.isBlank() || password == null || password.length == 0) {
            throw new AuthenticationException("Enter your email and password to sign in.");
        }
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        signInThrottle.requireNotLocked(normalizedEmail);
        Optional<UserCredentials> credentials = database.read(
                connection -> userDao.findCredentialsByEmail(connection, normalizedEmail));

        if (credentials.isEmpty()) {
            passwordHasher.verify(password, timingEqualizerHash);
            signInThrottle.recordFailure(normalizedEmail);
            throw AuthenticationException.invalidCredentials();
        }
        if (!passwordHasher.verify(password, credentials.get().passwordHash())) {
            LOGGER.info("Failed sign-in attempt for an existing account");
            signInThrottle.recordFailure(normalizedEmail);
            throw AuthenticationException.invalidCredentials();
        }
        signInThrottle.recordSuccess(normalizedEmail);
        User user = credentials.get().user();
        // The inactive message is only shown after the correct password, so it reveals nothing to a guesser.
        if (!user.isActive()) {
            throw new AuthenticationException(
                    "Your account is inactive. Contact your administrator if you need access again.");
        }
        database.read(connection -> {
            userDao.recordLogin(connection, user.id(), clock.instant());
            return null;
        });
        LOGGER.info("User " + user.id() + " signed in");
        return user;
    }

    /**
     * Changes the signed-in user's password after confirming their current one.
     */
    public void changePassword(CurrentUser currentUser, char[] currentPassword, char[] newPassword,
                               char[] confirmation) {
        if (currentPassword == null || currentPassword.length == 0) {
            throw new ValidationException("Enter your current password.");
        }
        if (newPassword == null || confirmation == null || !Arrays.equals(newPassword, confirmation)) {
            throw new ValidationException("The new passwords don't match.");
        }
        InputValidator.validateNewPassword(newPassword);
        if (Arrays.equals(currentPassword, newPassword)) {
            throw new ValidationException("Choose a new password that is different from your current one.");
        }

        database.inTransaction(connection -> {
            User user = authorization.requireActiveUser(connection, currentUser);
            String storedHash = userDao.findPasswordHash(connection, user.id()).orElse(null);
            if (!passwordHasher.verify(currentPassword, storedHash)) {
                throw new ValidationException("Your current password is incorrect.");
            }
            userDao.updatePasswordHash(connection, user.id(), passwordHasher.hash(newPassword));
            return null;
        });
        LOGGER.info("User " + currentUser.id() + " changed their password");
    }
}
