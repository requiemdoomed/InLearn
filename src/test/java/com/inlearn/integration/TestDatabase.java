package com.inlearn.integration;

import com.inlearn.dao.Database;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Connects integration tests to an isolated MySQL test database and rebuilds its schema from
 * the real scripts in {@code database/}.
 *
 * <p>Settings come from (first match wins) system properties {@code inlearn.test.db.url},
 * {@code inlearn.test.db.username} and {@code inlearn.test.db.password}; environment variables
 * {@code INLEARN_TEST_DB_URL}, {@code INLEARN_TEST_DB_USERNAME} and {@code INLEARN_TEST_DB_PASSWORD};
 * or {@code config/test-database.properties}. Without settings the tests are skipped, not failed.
 */
final class TestDatabase {

    private static final Path CONFIG_FILE = Path.of("config", "test-database.properties");
    private static final Path DROP_SCRIPT = Path.of("database", "99_drop_tables_development_only.sql");
    private static final Path SCHEMA_SCRIPT = Path.of("database", "02_schema.sql");
    private static final int CONNECT_TIMEOUT_SECONDS = 5;

    private final String url;
    private final String username;
    private final String password;

    private TestDatabase(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    /** Loads the settings, skipping the calling test class when none are configured. */
    static TestDatabase fromSettings() {
        Properties file = new Properties();
        if (Files.isRegularFile(CONFIG_FILE)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_FILE, StandardCharsets.UTF_8)) {
                file.load(reader);
            } catch (IOException e) {
                throw new IllegalStateException("Could not read " + CONFIG_FILE, e);
            }
        }
        String url = setting("inlearn.test.db.url", "INLEARN_TEST_DB_URL", file.getProperty("test.db.url"));
        String username = setting("inlearn.test.db.username", "INLEARN_TEST_DB_USERNAME", file.getProperty("test.db.username"));
        String password = setting("inlearn.test.db.password", "INLEARN_TEST_DB_PASSWORD", file.getProperty("test.db.password"));
        Assumptions.assumeTrue(url != null && !url.isBlank(),
                "No test database configured; see config/test-database.example.properties");
        String databaseName = url.replaceFirst("^jdbc:[a-z]+://[^/]+/", "").replaceFirst("[?;].*$", "");
        if (!databaseName.endsWith("_test")) {
            // The tests drop every table, so refuse anything that might be a real database.
            throw new IllegalStateException("Refusing to run integration tests against \"" + databaseName
                    + "\": the test database name must end with _test.");
        }
        return new TestDatabase(url, username, password == null ? "" : password);
    }

    Database database() {
        return new Database(url, username, password, CONNECT_TIMEOUT_SECONDS);
    }

    /** Drops and recreates all InLearn tables using the project's own SQL scripts. */
    void resetSchema() {
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            for (String sql : readStatements(DROP_SCRIPT)) {
                statement.execute(sql);
            }
            for (String sql : readStatements(SCHEMA_SCRIPT)) {
                statement.execute(sql);
            }
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Could not reset the test database schema: " + e.getMessage(), e);
        }
    }

    /** Splits a script into statements; the scripts keep semicolons out of comments and strings. */
    private static List<String> readStatements(Path script) throws IOException {
        StringBuilder withoutComments = new StringBuilder();
        for (String line : Files.readAllLines(script, StandardCharsets.UTF_8)) {
            if (!line.strip().startsWith("--")) {
                withoutComments.append(line).append('\n');
            }
        }
        List<String> statements = new ArrayList<>();
        for (String part : withoutComments.toString().split(";")) {
            if (!part.isBlank()) {
                statements.add(part.strip());
            }
        }
        return statements;
    }

    private static String setting(String systemProperty, String environmentVariable, String fileValue) {
        String value = System.getProperty(systemProperty);
        if (value == null || value.isBlank()) {
            value = System.getenv(environmentVariable);
        }
        return value == null || value.isBlank() ? fileValue : value;
    }

    static {
        DriverManager.setLoginTimeout(CONNECT_TIMEOUT_SECONDS);
    }
}
