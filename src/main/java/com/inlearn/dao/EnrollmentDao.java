package com.inlearn.dao;

import com.inlearn.model.Enrollment;
import com.inlearn.model.EnrollmentStatus;

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
 * JDBC access to the {@code enrollments} table.
 */
public class EnrollmentDao {

    private static final String ENROLLMENT_SELECT = """
            SELECT e.id, e.course_id, e.student_id, e.status, e.enrolled_at, e.withdrawn_at, e.version,
                   c.code AS course_code, c.title AS course_title,
                   s.full_name AS student_name, s.email AS student_email
              FROM enrollments e
              JOIN courses c ON c.id = e.course_id
              JOIN users s ON s.id = e.student_id
            """;

    public Optional<Enrollment> findByCourseAndStudent(Connection connection, long courseId, long studentId)
            throws SQLException {
        String sql = ENROLLMENT_SELECT + " WHERE e.course_id = ? AND e.student_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, courseId);
            statement.setLong(2, studentId);
            List<Enrollment> enrollments = queryList(statement);
            return enrollments.isEmpty() ? Optional.empty() : Optional.of(enrollments.getFirst());
        }
    }

    /** All enrollments for a course, including withdrawn ones, ordered by student name. */
    public List<Enrollment> findByCourse(Connection connection, long courseId) throws SQLException {
        String sql = ENROLLMENT_SELECT + " WHERE e.course_id = ? ORDER BY e.status, s.full_name";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, courseId);
            return queryList(statement);
        }
    }

    public List<Enrollment> findActiveByCourse(Connection connection, long courseId) throws SQLException {
        String sql = ENROLLMENT_SELECT + " WHERE e.course_id = ? AND e.status = 'ACTIVE' ORDER BY s.full_name";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, courseId);
            return queryList(statement);
        }
    }

    public boolean isActivelyEnrolled(Connection connection, long courseId, long studentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM enrollments WHERE course_id = ? AND student_id = ? AND status = 'ACTIVE'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, courseId);
            statement.setLong(2, studentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    public long insert(Connection connection, long courseId, long studentId, Instant enrolledAt) throws SQLException {
        String sql = "INSERT INTO enrollments (course_id, student_id, status, enrolled_at) VALUES (?, ?, 'ACTIVE', ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, courseId);
            statement.setLong(2, studentId);
            JdbcSupport.setInstant(statement, 3, enrolledAt);
            statement.executeUpdate();
            return JdbcSupport.generatedKey(statement);
        }
    }

    /** Re-enrolls a previously withdrawn student by reactivating their existing row. */
    public boolean reactivate(Connection connection, long enrollmentId, Instant enrolledAt, int expectedVersion)
            throws SQLException {
        String sql = "UPDATE enrollments SET status = 'ACTIVE', enrolled_at = ?, withdrawn_at = NULL, "
                + "version = version + 1 WHERE id = ? AND version = ? AND status = 'WITHDRAWN'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            JdbcSupport.setInstant(statement, 1, enrolledAt);
            statement.setLong(2, enrollmentId);
            statement.setInt(3, expectedVersion);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean withdraw(Connection connection, long enrollmentId, Instant withdrawnAt, int expectedVersion)
            throws SQLException {
        String sql = "UPDATE enrollments SET status = 'WITHDRAWN', withdrawn_at = ?, version = version + 1 "
                + "WHERE id = ? AND version = ? AND status = 'ACTIVE'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            JdbcSupport.setInstant(statement, 1, withdrawnAt);
            statement.setLong(2, enrollmentId);
            statement.setInt(3, expectedVersion);
            return statement.executeUpdate() == 1;
        }
    }

    /** Active enrollments of active students in active courses. */
    public int countActive(Connection connection) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                  FROM enrollments e
                  JOIN courses c ON c.id = e.course_id AND c.status = 'ACTIVE'
                  JOIN users s ON s.id = e.student_id AND s.status = 'ACTIVE'
                 WHERE e.status = 'ACTIVE'
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    /** Distinct active students enrolled in any active course taught by the instructor. */
    public int countActiveStudentsForInstructor(Connection connection, long instructorId) throws SQLException {
        String sql = """
                SELECT COUNT(DISTINCT e.student_id)
                  FROM enrollments e
                  JOIN courses c ON c.id = e.course_id AND c.status = 'ACTIVE'
                  JOIN users s ON s.id = e.student_id AND s.status = 'ACTIVE'
                 WHERE e.status = 'ACTIVE' AND c.instructor_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, instructorId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private List<Enrollment> queryList(PreparedStatement statement) throws SQLException {
        List<Enrollment> enrollments = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                enrollments.add(new Enrollment(
                        resultSet.getLong("id"),
                        resultSet.getLong("course_id"),
                        resultSet.getString("course_code"),
                        resultSet.getString("course_title"),
                        resultSet.getLong("student_id"),
                        resultSet.getString("student_name"),
                        resultSet.getString("student_email"),
                        EnrollmentStatus.valueOf(resultSet.getString("status")),
                        JdbcSupport.getInstant(resultSet, "enrolled_at"),
                        JdbcSupport.getInstant(resultSet, "withdrawn_at"),
                        resultSet.getInt("version")));
            }
        }
        return enrollments;
    }
}
