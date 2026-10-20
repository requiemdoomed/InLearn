package com.inlearn.dao;

import com.inlearn.model.AccountStatus;
import com.inlearn.model.Role;
import com.inlearn.model.ThemePreference;
import com.inlearn.model.User;
import com.inlearn.model.UserCredentials;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC access to the {@code users} table.
 */
public class UserDao {

    private static final String USER_COLUMNS =
            "id, email, full_name, role, status, theme_preference, last_login_at, version, created_at";

    public Optional<User> findById(Connection connection, long userId) throws SQLException {
        String sql = "SELECT " + USER_COLUMNS + " FROM users WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            return querySingle(statement);
        }
    }

    /** Loads and row-locks a user for the rest of the current transaction. */
    public Optional<User> findByIdForUpdate(Connection connection, long userId) throws SQLException {
        String sql = "SELECT " + USER_COLUMNS + " FROM users WHERE id = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, userId);
            return querySingle(statement);
        }
    }

    public Optional<UserCredentials> findCredentialsByEmail(Connection connection, String email) throws SQLException {
        String sql = "SELECT " + USER_COLUMNS + ", password_hash FROM users WHERE email = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(new UserCredentials(mapUser(resultSet), resultSet.getString("password_hash")));
            }
        }
    }

    public Optional<String> findPasswordHash(Connection connection, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT password_hash FROM users WHERE id = ?")) {
            statement.setLong(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(resultSet.getString(1)) : Optional.empty();
            }
        }
    }

    /** True if another account already uses the email address (case-insensitive). */
    public boolean emailInUse(Connection connection, String email, Long excludedUserId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ? AND (? IS NULL OR id <> ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            JdbcSupport.setNullableLong(statement, 2, excludedUserId);
            JdbcSupport.setNullableLong(statement, 3, excludedUserId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    public List<User> findAll(Connection connection) throws SQLException {
        String sql = "SELECT " + USER_COLUMNS + " FROM users ORDER BY full_name, email";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            return queryList(statement);
        }
    }

    public List<User> findActiveByRole(Connection connection, Role role) throws SQLException {
        String sql = "SELECT " + USER_COLUMNS + " FROM users WHERE role = ? AND status = 'ACTIVE' ORDER BY full_name";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, role.name());
            return queryList(statement);
        }
    }

    public int countActiveByRole(Connection connection, Role role) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM users WHERE role = ? AND status = 'ACTIVE'")) {
            statement.setString(1, role.name());
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    /**
     * Counts active administrators while locking their rows, so two administrators cannot
     * deactivate each other at the same moment and leave the system with none.
     */
    public int countActiveAdministratorsForUpdate(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM users WHERE role = 'ADMIN' AND status = 'ACTIVE' FOR UPDATE")) {
            int count = 0;
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    count++;
                }
            }
            return count;
        }
    }

    public long insert(Connection connection, String email, String passwordHash, String fullName, Role role)
            throws SQLException {
        String sql = "INSERT INTO users (email, password_hash, full_name, role, status, theme_preference) "
                + "VALUES (?, ?, ?, ?, 'ACTIVE', 'SYSTEM')";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, email);
            statement.setString(2, passwordHash);
            statement.setString(3, fullName);
            statement.setString(4, role.name());
            statement.executeUpdate();
            return JdbcSupport.generatedKey(statement);
        }
    }

    /**
     * Updates the administrator-editable account fields.
     *
     * @return {@code false} when the row's version no longer matches (someone else saved first)
     */
    public boolean updateAccount(Connection connection, long userId, String email, String fullName, Role role,
                                 AccountStatus status, int expectedVersion) throws SQLException {
        String sql = "UPDATE users SET email = ?, full_name = ?, role = ?, status = ?, version = version + 1 "
                + "WHERE id = ? AND version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            statement.setString(2, fullName);
            statement.setString(3, role.name());
            statement.setString(4, status.name());
            statement.setLong(5, userId);
            statement.setInt(6, expectedVersion);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean updateStatus(Connection connection, long userId, AccountStatus status, int expectedVersion)
            throws SQLException {
        String sql = "UPDATE users SET status = ?, version = version + 1 WHERE id = ? AND version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setLong(2, userId);
            statement.setInt(3, expectedVersion);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean updateDisplayName(Connection connection, long userId, String fullName, int expectedVersion)
            throws SQLException {
        String sql = "UPDATE users SET full_name = ?, version = version + 1 WHERE id = ? AND version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, fullName);
            statement.setLong(2, userId);
            statement.setInt(3, expectedVersion);
            return statement.executeUpdate() == 1;
        }
    }

    /** Password changes touch only the hash, so they never collide with profile edits. */
    public void updatePasswordHash(Connection connection, long userId, String passwordHash) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE users SET password_hash = ? WHERE id = ?")) {
            statement.setString(1, passwordHash);
            statement.setLong(2, userId);
            statement.executeUpdate();
        }
    }

    /**
     * Saves only the appearance preference. It deliberately leaves the version and every other
     * column alone so a theme switch can never overwrite or conflict with other profile changes.
     */
    public void updateThemePreference(Connection connection, long userId, ThemePreference preference)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE users SET theme_preference = ? WHERE id = ?")) {
            statement.setString(1, preference.name());
            statement.setLong(2, userId);
            statement.executeUpdate();
        }
    }

    public void recordLogin(Connection connection, long userId, Instant signedInAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE users SET last_login_at = ? WHERE id = ?")) {
            JdbcSupport.setInstant(statement, 1, signedInAt);
            statement.setLong(2, userId);
            statement.executeUpdate();
        }
    }

    public void delete(Connection connection, long userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM users WHERE id = ?")) {
            statement.setLong(1, userId);
            statement.executeUpdate();
        }
    }

    /** Counts the academic records that reference a user, used before role changes and deletions. */
    public UserRecordCounts countRelatedRecords(Connection connection, long userId) throws SQLException {
        String sql = """
                SELECT
                    (SELECT COUNT(*) FROM courses WHERE instructor_id = ?)          AS courses_taught,
                    (SELECT COUNT(*) FROM enrollments WHERE student_id = ?)         AS enrollments,
                    (SELECT COUNT(*) FROM submissions WHERE student_id = ?)         AS submissions,
                    (SELECT COUNT(*) FROM grades WHERE graded_by = ?)               AS grades_given,
                    (SELECT COUNT(*) FROM assignments WHERE created_by = ?)         AS assignments_created,
                    (SELECT COUNT(*) FROM course_materials WHERE uploaded_by = ?)   AS materials_shared,
                    (SELECT COUNT(*) FROM announcements WHERE author_id = ?)        AS announcements_posted
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 1; index <= 7; index++) {
                statement.setLong(index, userId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return new UserRecordCounts(
                        resultSet.getInt("courses_taught"),
                        resultSet.getInt("enrollments"),
                        resultSet.getInt("submissions"),
                        resultSet.getInt("grades_given"),
                        resultSet.getInt("assignments_created"),
                        resultSet.getInt("materials_shared"),
                        resultSet.getInt("announcements_posted"));
            }
        }
    }

    private Optional<User> querySingle(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? Optional.of(mapUser(resultSet)) : Optional.empty();
        }
    }

    private List<User> queryList(PreparedStatement statement) throws SQLException {
        List<User> users = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                users.add(mapUser(resultSet));
            }
        }
        return users;
    }

    private User mapUser(ResultSet resultSet) throws SQLException {
        return new User(
                resultSet.getLong("id"),
                resultSet.getString("email"),
                resultSet.getString("full_name"),
                Role.valueOf(resultSet.getString("role")),
                AccountStatus.valueOf(resultSet.getString("status")),
                ThemePreference.fromStoredValue(resultSet.getString("theme_preference")),
                JdbcSupport.getInstant(resultSet, "last_login_at"),
                resultSet.getInt("version"),
                JdbcSupport.getInstant(resultSet, "created_at"));
    }

    /** How many academic records reference a user. */
    public record UserRecordCounts(
            int coursesTaught,
            int enrollments,
            int submissions,
            int gradesGiven,
            int assignmentsCreated,
            int materialsShared,
            int announcementsPosted) {

        public boolean hasAny() {
            return coursesTaught + enrollments + submissions + gradesGiven + assignmentsCreated
                    + materialsShared + announcementsPosted > 0;
        }
    }
}
