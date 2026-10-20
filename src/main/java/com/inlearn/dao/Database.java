package com.inlearn.dao;

import com.inlearn.config.AppConfig;
import com.inlearn.exception.ConflictException;
import com.inlearn.exception.DataAccessException;
import com.inlearn.exception.DatabaseUnavailableException;
import com.inlearn.exception.DuplicateRecordException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTransientConnectionException;
import java.sql.Statement;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Opens JDBC connections and runs work inside them, translating {@link SQLException}s into the
 * application's user-facing exceptions.
 *
 * <p>Every connection's session time zone is set to UTC, which is the storage convention for
 * all DATETIME columns (see database/02_schema.sql).
 *
 * <p>A new connection is opened for each unit of work and closed afterwards. For a desktop
 * client with a handful of concurrent background tasks this keeps resource handling simple and
 * avoids holding idle connections open.
 */
public class Database {

    private static final Logger LOGGER = Logger.getLogger(Database.class.getName());

    /** MySQL error codes we translate into specific exceptions. */
    private static final int MYSQL_DUPLICATE_ENTRY = 1062;
    private static final int MYSQL_ROW_IS_REFERENCED = 1451;
    private static final int MYSQL_NO_REFERENCED_ROW = 1452;
    private static final int MYSQL_ACCESS_DENIED = 1045;
    private static final int MYSQL_UNKNOWN_DATABASE = 1049;
    private static final int MYSQL_UNKNOWN_TABLE = 1146;
    private static final String CONNECTION_ERROR_SQL_STATE_PREFIX = "08";

    private final String url;
    private final String username;
    private final String password;
    private final int connectTimeoutSeconds;

    public Database(String url, String username, String password, int connectTimeoutSeconds) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public static Database fromConfig(AppConfig config) {
        return new Database(config.databaseUrl(), config.databaseUsername(), config.databasePassword(),
                config.databaseConnectTimeoutSeconds());
    }

    /** Runs read-only or single-statement work with auto-commit enabled. */
    public <T> T read(SqlWork<T> work) {
        try (Connection connection = openConnection()) {
            return work.execute(connection);
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    /**
     * Runs work in a single transaction: everything commits together, or nothing does.
     * Any exception — SQL or business rule — rolls the transaction back and is rethrown.
     */
    public <T> T inTransaction(SqlWork<T> work) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.execute(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(connection, e);
                throw e;
            }
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    /** Confirms the database is reachable; used at startup to show a helpful message early. */
    public void checkConnection() {
        read(connection -> connection.isValid(connectTimeoutSeconds));
    }

    private Connection openConnection() throws SQLException {
        Properties connectionProperties = new Properties();
        connectionProperties.setProperty("user", username);
        connectionProperties.setProperty("password", password);
        DriverManager.setLoginTimeout(connectTimeoutSeconds);
        Connection connection = DriverManager.getConnection(url, connectionProperties);
        try (Statement statement = connection.createStatement()) {
            statement.execute("SET time_zone = '+00:00'");
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }

    private static void rollbackQuietly(Connection connection, Exception originalFailure) {
        try {
            connection.rollback();
        } catch (SQLException rollbackFailure) {
            // Keep the original failure as the primary error but record the rollback problem too.
            originalFailure.addSuppressed(rollbackFailure);
            LOGGER.log(Level.WARNING, "Transaction rollback failed", rollbackFailure);
        }
    }

    /** Converts a JDBC failure into an exception whose message is safe to show to users. */
    static RuntimeException translate(SQLException e) {
        // Credential and missing-schema problems are checked first: some drivers report them as
        // connection failures, but "try again" would not help, so they get their own guidance.
        if (e.getErrorCode() == MYSQL_ACCESS_DENIED || "28000".equals(e.getSQLState())) {
            LOGGER.log(Level.WARNING, "Database rejected the configured credentials");
            return new DataAccessException("The database rejected InLearn's username or password. "
                    + "Check db.username and db.password in the configuration file.", e);
        }
        if (e.getErrorCode() == MYSQL_UNKNOWN_DATABASE) {
            LOGGER.log(Level.WARNING, "Configured database does not exist: " + e.getMessage());
            return new DataAccessException("The database named in db.url doesn't exist yet. "
                    + "Run database/01_create_database.sql and database/02_schema.sql first.", e);
        }
        if (e.getErrorCode() == MYSQL_UNKNOWN_TABLE) {
            LOGGER.log(Level.WARNING, "InLearn tables are missing: " + e.getMessage());
            return new DataAccessException("The InLearn tables haven't been created in this database. "
                    + "Run database/02_schema.sql, then restart InLearn.", e);
        }
        if (isConnectionFailure(e)) {
            LOGGER.log(Level.WARNING, "Database connection failed: " + e.getMessage());
            return new DatabaseUnavailableException(e);
        }
        if (e instanceof SQLIntegrityConstraintViolationException || "23000".equals(e.getSQLState())) {
            if (e.getErrorCode() == MYSQL_DUPLICATE_ENTRY) {
                return new DuplicateRecordException(e);
            }
            if (e.getErrorCode() == MYSQL_ROW_IS_REFERENCED || e.getErrorCode() == MYSQL_NO_REFERENCED_ROW) {
                LOGGER.log(Level.INFO, "Foreign key constraint rejected a change", e);
                return new ConflictException("This change can't be saved because it would break links to "
                        + "other records (for example courses, enrollments or grades). Refresh and try again.", e);
            }
        }
        LOGGER.log(Level.SEVERE, "Unexpected database error", e);
        return new DataAccessException(e);
    }

    private static boolean isConnectionFailure(SQLException e) {
        String sqlState = e.getSQLState();
        return e instanceof SQLNonTransientConnectionException
                || e instanceof SQLTransientConnectionException
                || (sqlState != null && sqlState.startsWith(CONNECTION_ERROR_SQL_STATE_PREFIX));
    }
}
