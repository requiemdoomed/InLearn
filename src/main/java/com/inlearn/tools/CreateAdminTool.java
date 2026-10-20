package com.inlearn.tools;

import com.inlearn.config.AppConfig;
import com.inlearn.config.LoggingSetup;
import com.inlearn.dao.Database;
import com.inlearn.dao.UserDao;
import com.inlearn.exception.ConflictException;
import com.inlearn.exception.InLearnException;
import com.inlearn.exception.ValidationException;
import com.inlearn.model.Role;
import com.inlearn.util.InputValidator;
import com.inlearn.util.PasswordHasher;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Creates an administrator account from the command line. This is how a fresh installation gets
 * its first administrator, because InLearn has no public registration and no built-in default
 * credentials.
 *
 * <p>Run with: {@code mvn -q compile exec:java@create-admin}
 */
public final class CreateAdminTool {

    private CreateAdminTool() {
    }

    public static void main(String[] args) {
        try {
            AppConfig config = AppConfig.load();
            LoggingSetup.configure(config.loggingDirectory());
            new CreateAdminTool().run(Database.fromConfig(config));
        } catch (InLearnException e) {
            System.err.println();
            System.err.println("Could not create the administrator: " + e.getMessage());
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Could not read your input: " + e.getMessage());
            System.exit(1);
        }
    }

    private void run(Database database) throws IOException {
        UserDao userDao = new UserDao();
        Prompter prompter = new Prompter();
        System.out.println("Create an InLearn administrator account");
        System.out.println("---------------------------------------");

        int existingAdministrators = database.read(connection -> userDao.countActiveByRole(connection, Role.ADMIN));
        if (existingAdministrators > 0) {
            System.out.println("Note: " + existingAdministrators + " active administrator account(s) already exist. "
                    + "Administrators can also create accounts from the Users screen.");
        }

        String fullName = InputValidator.requireText(prompter.readLine("Full name: "), "Full name",
                InputValidator.MAX_NAME_LENGTH);
        String email = InputValidator.normalizeEmail(prompter.readLine("Email: "));
        char[] password = prompter.readPassword("Password (min. " + InputValidator.MIN_PASSWORD_LENGTH
                + " characters, with a letter and a number): ");
        char[] confirmation = prompter.readPassword("Confirm password: ");
        try {
            if (!Arrays.equals(password, confirmation)) {
                throw new ValidationException("The passwords don't match.");
            }
            InputValidator.validateNewPassword(password);
            String passwordHash = new PasswordHasher().hash(password);
            long userId = database.inTransaction(connection -> {
                if (userDao.emailInUse(connection, email, null)) {
                    throw new ConflictException("An account with the email " + email + " already exists.");
                }
                return userDao.insert(connection, email, passwordHash, fullName, Role.ADMIN);
            });
            System.out.println();
            System.out.println("Administrator account created (id " + userId + "). You can now sign in to InLearn as "
                    + email + ".");
        } finally {
            Arrays.fill(password, '\0');
            Arrays.fill(confirmation, '\0');
        }
    }

    /** Reads input from the terminal, hiding passwords when a real console is available. */
    private static final class Prompter {
        private final Console console = System.console();
        private final BufferedReader fallbackReader =
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        String readLine(String prompt) throws IOException {
            if (console != null) {
                return console.readLine(prompt);
            }
            System.out.print(prompt);
            System.out.flush();
            return fallbackReader.readLine();
        }

        char[] readPassword(String prompt) throws IOException {
            if (console != null) {
                char[] password = console.readPassword(prompt);
                return password == null ? new char[0] : password;
            }
            // Without a console (for example inside some IDEs) the password can't be hidden.
            System.out.print("(input will be visible) " + prompt);
            System.out.flush();
            String line = fallbackReader.readLine();
            return line == null ? new char[0] : line.toCharArray();
        }
    }
}
