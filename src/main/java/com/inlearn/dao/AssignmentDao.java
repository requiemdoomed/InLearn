package com.inlearn.dao;

import com.inlearn.model.Assignment;
import com.inlearn.model.InstructorDashboard.UpcomingAssignment;
import com.inlearn.model.LatePolicy;

import java.math.BigDecimal;
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
 * JDBC access to the {@code assignments} table.
 */
public class AssignmentDao {

    private static final String ASSIGNMENT_COLUMNS =
            "a.id, a.course_id, a.title, a.instructions, a.due_at, a.max_points, a.late_policy, "
                    + "a.late_cutoff_at, a.version, a.created_at";

    public Optional<Assignment> findById(Connection connection, long assignmentId) throws SQLException {
        String sql = "SELECT " + ASSIGNMENT_COLUMNS + " FROM assignments a WHERE a.id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assignmentId);
            return querySingle(statement);
        }
    }

    /** Loads and row-locks an assignment so submissions and edits are checked against a stable deadline. */
    public Optional<Assignment> findByIdForUpdate(Connection connection, long assignmentId) throws SQLException {
        String sql = "SELECT " + ASSIGNMENT_COLUMNS + " FROM assignments a WHERE a.id = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assignmentId);
            return querySingle(statement);
        }
    }

    public List<Assignment> findByCourse(Connection connection, long courseId) throws SQLException {
        String sql = "SELECT " + ASSIGNMENT_COLUMNS + " FROM assignments a WHERE a.course_id = ? ORDER BY a.due_at, a.id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, courseId);
            List<Assignment> assignments = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    assignments.add(map(resultSet));
                }
            }
            return assignments;
        }
    }

    public long insert(Connection connection, long courseId, String title, String instructions, Instant dueAt,
                       BigDecimal maxPoints, LatePolicy latePolicy, Instant lateCutoffAt, long createdBy)
            throws SQLException {
        String sql = "INSERT INTO assignments (course_id, title, instructions, due_at, max_points, late_policy, "
                + "late_cutoff_at, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, courseId);
            statement.setString(2, title);
            statement.setString(3, instructions);
            JdbcSupport.setInstant(statement, 4, dueAt);
            statement.setBigDecimal(5, maxPoints);
            statement.setString(6, latePolicy.name());
            JdbcSupport.setInstant(statement, 7, lateCutoffAt);
            statement.setLong(8, createdBy);
            statement.executeUpdate();
            return JdbcSupport.generatedKey(statement);
        }
    }

    /** @return {@code false} if the assignment changed since it was loaded */
    public boolean update(Connection connection, long assignmentId, String title, String instructions, Instant dueAt,
                          BigDecimal maxPoints, LatePolicy latePolicy, Instant lateCutoffAt, int expectedVersion)
            throws SQLException {
        String sql = "UPDATE assignments SET title = ?, instructions = ?, due_at = ?, max_points = ?, late_policy = ?, "
                + "late_cutoff_at = ?, version = version + 1 WHERE id = ? AND version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, title);
            statement.setString(2, instructions);
            JdbcSupport.setInstant(statement, 3, dueAt);
            statement.setBigDecimal(4, maxPoints);
            statement.setString(5, latePolicy.name());
            JdbcSupport.setInstant(statement, 6, lateCutoffAt);
            statement.setLong(7, assignmentId);
            statement.setInt(8, expectedVersion);
            return statement.executeUpdate() == 1;
        }
    }

    public void delete(Connection connection, long assignmentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM assignments WHERE id = ?")) {
            statement.setLong(1, assignmentId);
            statement.executeUpdate();
        }
    }

    /** Assignments due after {@code now} in the instructor's active courses, soonest first. */
    public List<UpcomingAssignment> findUpcomingForInstructor(Connection connection, long instructorId, Instant now,
                                                              int limit) throws SQLException {
        String sql = """
                SELECT a.id, a.course_id, c.code, a.title, a.due_at,
                       (SELECT COUNT(*) FROM submissions s
                          JOIN enrollments e ON e.course_id = c.id AND e.student_id = s.student_id
                                            AND e.status = 'ACTIVE'
                         WHERE s.assignment_id = a.id) AS submission_count,
                       (SELECT COUNT(*) FROM enrollments e
                         WHERE e.course_id = c.id AND e.status = 'ACTIVE') AS enrolled_count
                  FROM assignments a
                  JOIN courses c ON c.id = a.course_id
                 WHERE c.instructor_id = ? AND c.status = 'ACTIVE' AND a.due_at > ?
                 ORDER BY a.due_at
                 LIMIT ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, instructorId);
            JdbcSupport.setInstant(statement, 2, now);
            statement.setInt(3, limit);
            List<UpcomingAssignment> upcoming = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    upcoming.add(new UpcomingAssignment(
                            resultSet.getLong("id"),
                            resultSet.getLong("course_id"),
                            resultSet.getString("code"),
                            resultSet.getString("title"),
                            JdbcSupport.getInstant(resultSet, "due_at"),
                            resultSet.getInt("submission_count"),
                            resultSet.getInt("enrolled_count")));
                }
            }
            return upcoming;
        }
    }

    private Optional<Assignment> querySingle(PreparedStatement statement) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
        }
    }

    private Assignment map(ResultSet resultSet) throws SQLException {
        return new Assignment(
                resultSet.getLong("id"),
                resultSet.getLong("course_id"),
                resultSet.getString("title"),
                resultSet.getString("instructions"),
                JdbcSupport.getInstant(resultSet, "due_at"),
                resultSet.getBigDecimal("max_points"),
                LatePolicy.valueOf(resultSet.getString("late_policy")),
                JdbcSupport.getInstant(resultSet, "late_cutoff_at"),
                resultSet.getInt("version"),
                JdbcSupport.getInstant(resultSet, "created_at"));
    }
}
