package com.inlearn.dao;

import com.inlearn.model.Grade;
import com.inlearn.model.StudentDashboard.RecentGrade;

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
 * JDBC access to the {@code grades} table.
 */
public class GradeDao {

    private static final String GRADE_SELECT = """
            SELECT g.id, g.submission_id, g.points, g.feedback, g.graded_by, g.graded_at, g.version,
                   grader.full_name AS graded_by_name
              FROM grades g
              JOIN users grader ON grader.id = g.graded_by
            """;

    public Optional<Grade> findBySubmission(Connection connection, long submissionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(GRADE_SELECT + " WHERE g.submission_id = ?")) {
            statement.setLong(1, submissionId);
            List<Grade> grades = queryList(statement);
            return grades.isEmpty() ? Optional.empty() : Optional.of(grades.getFirst());
        }
    }

    public List<Grade> findByAssignment(Connection connection, long assignmentId) throws SQLException {
        String sql = GRADE_SELECT + " JOIN submissions s ON s.id = g.submission_id WHERE s.assignment_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assignmentId);
            return queryList(statement);
        }
    }

    public List<Grade> findByCourse(Connection connection, long courseId) throws SQLException {
        String sql = GRADE_SELECT + """
                 JOIN submissions s ON s.id = g.submission_id
                 JOIN assignments a ON a.id = s.assignment_id
                WHERE a.course_id = ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, courseId);
            return queryList(statement);
        }
    }

    public List<Grade> findByStudent(Connection connection, long studentId) throws SQLException {
        String sql = GRADE_SELECT + " JOIN submissions s ON s.id = g.submission_id WHERE s.student_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            return queryList(statement);
        }
    }

    public long insert(Connection connection, long submissionId, BigDecimal points, String feedback, long gradedBy,
                       Instant gradedAt) throws SQLException {
        String sql = "INSERT INTO grades (submission_id, points, feedback, graded_by, graded_at) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, submissionId);
            statement.setBigDecimal(2, points);
            JdbcSupport.setNullableString(statement, 3, feedback);
            statement.setLong(4, gradedBy);
            JdbcSupport.setInstant(statement, 5, gradedAt);
            statement.executeUpdate();
            return JdbcSupport.generatedKey(statement);
        }
    }

    /** @return {@code false} if the grade changed since it was loaded */
    public boolean update(Connection connection, long gradeId, BigDecimal points, String feedback, long gradedBy,
                          Instant gradedAt, int expectedVersion) throws SQLException {
        String sql = "UPDATE grades SET points = ?, feedback = ?, graded_by = ?, graded_at = ?, version = version + 1 "
                + "WHERE id = ? AND version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, points);
            JdbcSupport.setNullableString(statement, 2, feedback);
            statement.setLong(3, gradedBy);
            JdbcSupport.setInstant(statement, 4, gradedAt);
            statement.setLong(5, gradeId);
            statement.setInt(6, expectedVersion);
            return statement.executeUpdate() == 1;
        }
    }

    public int countByAssignment(Connection connection, long assignmentId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM grades g JOIN submissions s ON s.id = g.submission_id WHERE s.assignment_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assignmentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    /** The student's most recently graded work in courses they are still enrolled in. */
    public List<RecentGrade> findRecentForStudent(Connection connection, long studentId, int limit) throws SQLException {
        String sql = """
                SELECT a.id AS assignment_id, a.course_id, c.code, a.title, g.points, a.max_points, g.graded_at
                  FROM grades g
                  JOIN submissions s ON s.id = g.submission_id
                  JOIN assignments a ON a.id = s.assignment_id
                  JOIN courses c ON c.id = a.course_id
                  JOIN enrollments e ON e.course_id = c.id AND e.student_id = s.student_id AND e.status = 'ACTIVE'
                 WHERE s.student_id = ?
                 ORDER BY g.graded_at DESC
                 LIMIT ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            statement.setInt(2, limit);
            List<RecentGrade> recent = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    recent.add(new RecentGrade(
                            resultSet.getLong("assignment_id"),
                            resultSet.getLong("course_id"),
                            resultSet.getString("code"),
                            resultSet.getString("title"),
                            resultSet.getBigDecimal("points"),
                            resultSet.getBigDecimal("max_points"),
                            JdbcSupport.getInstant(resultSet, "graded_at")));
                }
            }
            return recent;
        }
    }

    private List<Grade> queryList(PreparedStatement statement) throws SQLException {
        List<Grade> grades = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                grades.add(new Grade(
                        resultSet.getLong("id"),
                        resultSet.getLong("submission_id"),
                        resultSet.getBigDecimal("points"),
                        resultSet.getString("feedback"),
                        resultSet.getLong("graded_by"),
                        resultSet.getString("graded_by_name"),
                        JdbcSupport.getInstant(resultSet, "graded_at"),
                        resultSet.getInt("version")));
            }
        }
        return grades;
    }
}
