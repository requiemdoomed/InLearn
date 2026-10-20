package com.inlearn.service;

import com.inlearn.dao.Database;
import com.inlearn.dao.UserDao;
import com.inlearn.dao.UserDao.UserRecordCounts;
import com.inlearn.exception.ConcurrentUpdateException;
import com.inlearn.exception.ConflictException;
import com.inlearn.exception.DuplicateRecordException;
import com.inlearn.exception.NotFoundException;
import com.inlearn.exception.ValidationException;
import com.inlearn.model.AccountStatus;
import com.inlearn.model.CurrentUser;
import com.inlearn.model.Role;
import com.inlearn.model.User;
import com.inlearn.util.InputValidator;
import com.inlearn.util.PasswordHasher;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Administrator account management: creating, editing, deactivating and (when safe) deleting users.
 *
 * <p>Academic history is protected: a role change or deletion is refused while records such
 * as enrollments, submissions or taught courses still point at the account, and the message
 * explains how to resolve it. The last active administrator can never be removed or demoted.
 */
public class UserService {

    private static final Logger LOGGER = Logger.getLogger(UserService.class.getName());

    private final Database database;
    private final UserDao userDao;
    private final AuthorizationService authorization;
    private final PasswordHasher passwordHasher;

    public UserService(Database database, UserDao userDao, AuthorizationService authorization,
                       PasswordHasher passwordHasher) {
        this.database = database;
        this.userDao = userDao;
        this.authorization = authorization;
        this.passwordHasher = passwordHasher;
    }

    /** The editable details of an account. */
    public record AccountDetails(String email, String fullName, Role role) {
    }

    public List<User> listUsers(CurrentUser currentUser) {
        return database.read(connection -> {
            authorization.requireAdministrator(connection, currentUser);
            return userDao.findAll(connection);
        });
    }

    public List<User> listActiveUsers(CurrentUser currentUser, Role role) {
        return database.read(connection -> {
            authorization.requireAdministrator(connection, currentUser);
            return userDao.findActiveByRole(connection, role);
        });
    }

    /**
     * Creates an account with a temporary password that the administrator gives to the user.
     */
    public User createUser(CurrentUser currentUser, AccountDetails details, char[] temporaryPassword) {
        String fullName = InputValidator.requireText(details.fullName(), "Full name", InputValidator.MAX_NAME_LENGTH);
        String email = InputValidator.normalizeEmail(details.email());
        Role role = requireRole(details.role());
        InputValidator.validateNewPassword(temporaryPassword);
        String passwordHash = passwordHasher.hash(temporaryPassword);

        try {
            User created = database.inTransaction(connection -> {
                authorization.requireAdministrator(connection, currentUser);
                if (userDao.emailInUse(connection, email, null)) {
                    throw duplicateEmail(email);
                }
                long userId = userDao.insert(connection, email, passwordHash, fullName, role);
                return userDao.findById(connection, userId).orElseThrow();
            });
            LOGGER.info("Administrator " + currentUser.id() + " created user " + created.id() + " (" + role + ")");
            return created;
        } catch (DuplicateRecordException e) {
            throw duplicateEmail(email);
        }
    }

    /**
     * Saves changes to an account's email, name, role and status.
     *
     * @param expectedVersion the version the administrator loaded, for concurrent-edit detection
     */
    public User updateUser(CurrentUser currentUser, long userId, AccountDetails details, AccountStatus status,
                           int expectedVersion) {
        String fullName = InputValidator.requireText(details.fullName(), "Full name", InputValidator.MAX_NAME_LENGTH);
        String email = InputValidator.normalizeEmail(details.email());
        Role newRole = requireRole(details.role());
        if (status == null) {
            throw new ValidationException("Choose whether the account is active or inactive.");
        }

        try {
            return database.inTransaction(connection -> {
                User administrator = authorization.requireAdministrator(connection, currentUser);
                User target = lockUser(connection, userId);
                if (userDao.emailInUse(connection, email, userId)) {
                    throw duplicateEmail(email);
                }
                if (newRole != target.role()) {
                    checkRoleChangeAllowed(connection, administrator, target, newRole);
                }
                if (status == AccountStatus.INACTIVE && target.isActive()) {
                    checkDeactivationAllowed(connection, administrator, target);
                }
                if (!userDao.updateAccount(connection, userId, email, fullName, newRole, status, expectedVersion)) {
                    throw new ConcurrentUpdateException("account");
                }
                return userDao.findById(connection, userId).orElseThrow();
            });
        } catch (DuplicateRecordException e) {
            throw duplicateEmail(email);
        }
    }

    /** Activates or deactivates an account without changing anything else. */
    public User setStatus(CurrentUser currentUser, long userId, AccountStatus status, int expectedVersion) {
        return database.inTransaction(connection -> {
            User administrator = authorization.requireAdministrator(connection, currentUser);
            User target = lockUser(connection, userId);
            if (status == AccountStatus.INACTIVE && target.isActive()) {
                checkDeactivationAllowed(connection, administrator, target);
            }
            if (!userDao.updateStatus(connection, userId, status, expectedVersion)) {
                throw new ConcurrentUpdateException("account");
            }
            LOGGER.info("Administrator " + administrator.id() + " set user " + userId + " to " + status);
            return userDao.findById(connection, userId).orElseThrow();
        });
    }

    /** Sets a new temporary password for another user (for example when they forget theirs). */
    public void resetPassword(CurrentUser currentUser, long userId, char[] newPassword, char[] confirmation) {
        if (newPassword == null || confirmation == null || !Arrays.equals(newPassword, confirmation)) {
            throw new ValidationException("The passwords don't match.");
        }
        InputValidator.validateNewPassword(newPassword);
        String passwordHash = passwordHasher.hash(newPassword);
        database.inTransaction(connection -> {
            User administrator = authorization.requireAdministrator(connection, currentUser);
            lockUser(connection, userId);
            userDao.updatePasswordHash(connection, userId, passwordHash);
            LOGGER.info("Administrator " + administrator.id() + " reset the password of user " + userId);
            return null;
        });
    }

    /**
     * Permanently deletes an account that was created by mistake. Accounts with any academic
     * history must be deactivated instead so the history is preserved.
     */
    public void deleteUser(CurrentUser currentUser, long userId) {
        database.inTransaction(connection -> {
            User administrator = authorization.requireAdministrator(connection, currentUser);
            User target = lockUser(connection, userId);
            if (target.id() == administrator.id()) {
                throw new ConflictException("You can't delete your own account.");
            }
            UserRecordCounts records = userDao.countRelatedRecords(connection, userId);
            if (records.hasAny()) {
                throw new ConflictException(target.fullName() + " has academic records (such as courses, "
                        + "enrollments, submissions or grades) that must be kept. Deactivate the account instead.");
            }
            if (target.hasRole(Role.ADMIN) && target.isActive()
                    && userDao.countActiveAdministratorsForUpdate(connection) <= 1) {
                throw lastAdministrator(target);
            }
            userDao.delete(connection, userId);
            LOGGER.info("Administrator " + administrator.id() + " deleted user " + userId);
            return null;
        });
    }

    private void checkRoleChangeAllowed(Connection connection, User administrator, User target, Role newRole)
            throws SQLException {
        if (target.id() == administrator.id()) {
            throw new ConflictException("You can't change your own role. Ask another administrator to do it.");
        }
        if (target.hasRole(Role.ADMIN) && target.isActive()
                && userDao.countActiveAdministratorsForUpdate(connection) <= 1) {
            throw lastAdministrator(target);
        }
        UserRecordCounts records = userDao.countRelatedRecords(connection, target.id());
        if (records.coursesTaught() > 0) {
            throw new ConflictException(target.fullName() + " is the instructor for " + plural(records.coursesTaught(), "course")
                    + ". Assign those courses to another instructor in Course Management, then change the role.");
        }
        if (records.enrollments() > 0 || records.submissions() > 0) {
            throw new ConflictException(target.fullName() + " has enrollment history in "
                    + plural(records.enrollments(), "course") + ". Changing the role would detach that academic "
                    + "record, so create a separate account for the new role instead.");
        }
        if (records.hasAny()) {
            throw new ConflictException(target.fullName() + " has created course content or grades that must stay "
                    + "linked to their current role. Create a separate account for the new role instead.");
        }
        LOGGER.info("Administrator " + administrator.id() + " changed role of user " + target.id() + " to " + newRole);
    }

    private void checkDeactivationAllowed(Connection connection, User administrator, User target) throws SQLException {
        if (target.id() == administrator.id()) {
            throw new ConflictException("You can't deactivate your own account while you're signed in to it.");
        }
        if (target.hasRole(Role.ADMIN) && userDao.countActiveAdministratorsForUpdate(connection) <= 1) {
            throw lastAdministrator(target);
        }
    }

    private User lockUser(Connection connection, long userId) throws SQLException {
        return userDao.findByIdForUpdate(connection, userId)
                .orElseThrow(() -> new NotFoundException("This account no longer exists. Refresh the user list."));
    }

    private static Role requireRole(Role role) {
        if (role == null) {
            throw new ValidationException("Choose a role for the account.");
        }
        return role;
    }

    private static ConflictException duplicateEmail(String email) {
        return new ConflictException("Another account already uses " + email + ". Each account needs a unique email.");
    }

    private static ConflictException lastAdministrator(User target) {
        return new ConflictException(target.fullName() + " is the only active administrator. "
                + "Create or reactivate another administrator account first.");
    }

    private static String plural(int count, String noun) {
        return count + " " + noun + (count == 1 ? "" : "s");
    }
}
