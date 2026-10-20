package com.inlearn.integration;

import com.inlearn.exception.AuthenticationException;
import com.inlearn.exception.ConflictException;
import com.inlearn.model.AccountStatus;
import com.inlearn.model.CurrentUser;
import com.inlearn.model.Role;
import com.inlearn.model.ThemePreference;
import com.inlearn.model.User;
import com.inlearn.service.SignInThrottle;
import com.inlearn.service.UserService.AccountDetails;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Accounts: sign-in, duplicates, protected role changes and per-user appearance. */
class UserManagementIT extends IntegrationTestBase {

    @Test
    void signInChecksPasswordAndStatusWithGenericMessages() {
        createUser("s@test.edu", "Student", Role.STUDENT);
        assertEquals("s@test.edu", app.authService().signIn("S@Test.edu", PASSWORD.toCharArray()).email());

        AuthenticationException wrongPassword = assertThrows(AuthenticationException.class,
                () -> app.authService().signIn("s@test.edu", "Wrong#Pass1".toCharArray()));
        AuthenticationException unknownEmail = assertThrows(AuthenticationException.class,
                () -> app.authService().signIn("nobody@test.edu", PASSWORD.toCharArray()));
        assertEquals(wrongPassword.getMessage(), unknownEmail.getMessage(), "messages must not reveal which emails exist");
    }

    @Test
    void repeatedWrongPasswordsLockSignInTemporarilyEvenForTheRightPassword() {
        createUser("s@test.edu", "Student", Role.STUDENT);
        for (int attempt = 0; attempt < SignInThrottle.MAX_FAILED_ATTEMPTS; attempt++) {
            assertThrows(AuthenticationException.class,
                    () -> app.authService().signIn("s@test.edu", "Wrong#Pass1".toCharArray()));
        }
        AuthenticationException locked = assertThrows(AuthenticationException.class,
                () -> app.authService().signIn("s@test.edu", PASSWORD.toCharArray()));
        assertTrue(locked.getMessage().contains("Too many unsuccessful sign-in attempts"));

        clock.advance(SignInThrottle.LOCKOUT_DURATION.plusSeconds(1));
        assertEquals("s@test.edu", app.authService().signIn("s@test.edu", PASSWORD.toCharArray()).email());
    }

    @Test
    void duplicateEmailsAreRejectedCaseInsensitively() {
        createUser("dup@test.edu", "First", Role.STUDENT);
        assertThrows(ConflictException.class, () -> createUser("DUP@test.edu", "Second", Role.STUDENT));
    }

    @Test
    void theRemainingAdministratorCannotDeactivateOrDemoteThemselves() {
        CurrentUser secondAdmin = createUser("admin2@test.edu", "Second Admin", Role.ADMIN);
        User firstAdmin = find(admin.id());
        User remaining = find(secondAdmin.id());
        // The second admin deactivates the first: allowed while two remain.
        app.userService().setStatus(secondAdmin, firstAdmin.id(), AccountStatus.INACTIVE, firstAdmin.version());
        // Nobody can now remove the only remaining active administrator.
        assertThrows(ConflictException.class, () -> app.userService().updateUser(secondAdmin, remaining.id(),
                new AccountDetails(remaining.email(), remaining.fullName(), Role.STUDENT), AccountStatus.ACTIVE,
                remaining.version()));
        assertThrows(ConflictException.class,
                () -> app.userService().setStatus(secondAdmin, remaining.id(), AccountStatus.INACTIVE, remaining.version()));
    }

    @Test
    void roleChangesAreBlockedWhenAcademicRecordsExist() {
        CurrentUser instructor = createUser("i@test.edu", "Instructor", Role.INSTRUCTOR);
        createCourse("HIST 100", instructor);
        User instructorAccount = find(instructor.id());
        ConflictException error = assertThrows(ConflictException.class, () -> app.userService().updateUser(admin,
                instructor.id(), new AccountDetails(instructorAccount.email(), instructorAccount.fullName(), Role.STUDENT),
                AccountStatus.ACTIVE, instructorAccount.version()));
        assertTrue(error.getMessage().contains("Assign those courses to another instructor"));
    }

    @Test
    void passwordChangeRequiresTheCurrentPassword() {
        CurrentUser student = createUser("s@test.edu", "Student", Role.STUDENT);
        assertThrows(RuntimeException.class, () -> app.authService().changePassword(student,
                "Wrong#Pass1".toCharArray(), "New#Pass2026".toCharArray(), "New#Pass2026".toCharArray()));
        app.authService().changePassword(student, PASSWORD.toCharArray(), "New#Pass2026".toCharArray(),
                "New#Pass2026".toCharArray());
        assertEquals(student.id(), app.authService().signIn("s@test.edu", "New#Pass2026".toCharArray()).id());
    }

    @Test
    void eachUserKeepsTheirOwnThemeWithoutTouchingOtherProfileData() {
        CurrentUser first = createUser("one@test.edu", "First Person", Role.STUDENT);
        CurrentUser second = createUser("two@test.edu", "Second Person", Role.STUDENT);
        User before = find(first.id());

        app.profileService().saveThemePreference(first, ThemePreference.DARK);
        app.profileService().saveThemePreference(second, ThemePreference.LIGHT);

        User after = find(first.id());
        assertEquals(ThemePreference.DARK, after.themePreference());
        assertEquals(ThemePreference.LIGHT, find(second.id()).themePreference());
        assertEquals(before.fullName(), after.fullName());
        assertEquals(before.version(), after.version(), "a theme change must not conflict with profile edits");
        // A profile edit that started before the theme change still saves.
        app.profileService().updateDisplayName(first, "First P. Person", before.version());
    }

    private User find(long userId) {
        return app.userService().listUsers(admin).stream().filter(user -> user.id() == userId).findFirst().orElseThrow();
    }
}
