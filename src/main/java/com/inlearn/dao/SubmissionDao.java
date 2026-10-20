package com.inlearn.dao;

import com.inlearn.model.InstructorDashboard.PendingSubmission;
import com.inlearn.model.Submission;

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
 * JDBC access to the {@code submissions} table.
 */
public class SubmissionDao {

    private static final String SUBMISSION_SELECT = """
            SELECT s.id, s.assignment_id, s.student_id, s.original_filename, s.storage_key, s.file_size_bytes,
                   s.submitted_at, s.is_late, s.attempt_number, s.version,
                   student.full_name AS student_name
              FROM submissions s
              JOIN users student ON student.id = s.student_id
            """;

    public Optional<Submission> findById(Connection connection, long submissionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SUBMISSION_SELECT + " WHERE s.id = ?")) {
            statement.setLong(1, submissionId);
            return querySingle(statement);
        }
    }

    public Optional<Submission> findByAssignmentAndStudent(Connection connection, long assignmentId, long studentId)
            throws SQLException {
        String sql = SUBMISSION_SELECT + " WHERE s.assignment_id = ? AND s.student_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assignmentId);
            statement.setLong(2, studentId);
            return querySingle(statement);
        }
    }

    /** Locking variant used while deciding whether a submission may be created or replaced. */
    public Optional<Submission> findByAssignmentAndStudentForUpdate(Connection connection, long assignmentId,
                                                                    long studentId) throws SQLException {
        String sql = SUBMISSION_SELECT + " WHERE s.assignment_id = ? AND s.student_id = ? FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assignmentId);
            statement.setLong(2, studentId);
            return querySingle(statement);
        }
    }

    public List<Submission> findByAssignment(Connection connection, long assignmentId) throws SQLException {
        String sql = SUBMISSION_SELECT + " WHERE s.assignment_id = ? ORDER BY student.full_name";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, assignmentId);
            return queryList(statement);
        }
    }

    /** Every submission for every assignment in a course (used to build the gradebook). */
    public List<Submission> findByCourse(Connection connection, long courseId) throws SQLException {
        String sql = SUBMISSION_SELECT + " JOIN assignments a ON a.id = s.assignment_id WHERE a.course_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, courseId);
            return queryList(statement);
        }
    }

    public List<Submission> findByStudentInCourse(Connection connection, long studentId, long courseId)
            throws SQLException {
        String sql = SUBMISSION_SELECT
                + " JOIN assignments a ON a.id = s.assignment_id WHERE s.student_id = ? AND a.course_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            statement.setLong(2, courseId);
            return queryList(statement);
        }
    }

    /** All of a student's submissions in their active courses; used for dashboard status calculation. */
    public List<Submission> findByStudent(Connection connection, long studentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SUBMISSION_SELECT + " WHERE s.student_id = ?")) {
            statement.setLong(1, studentId);
            return queryList(statement);
        }
    }

    public long insert(Connection connection, long assignmentId, long studentId, String originalFilename,
                       String storageKey, long fileSizeBytes, Instant submittedAt, boolean late) throws SQLException {
        String sql = "INSERT INTO submissions (assignment_id, student_id, original_filename, storage_key, "
                + "file_size_bytes, submitted_at, is_late, attempt_number) VALUES (?, ?, ?, ?, ?, ?, ?, 1)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, assignmentId);
            statement.setLong(2, studentId);
            statement.setString(3, originalFilename);
            statement.setString(4, storageKey);
            statement.setLong(5, fileSizeBytes);
            JdbcSupport.setInstant(statement, 6, submittedAt);
            statement.setBoolean(7, late);
            statement.executeUpdate();
            return JdbcSupport.generatedKey(statement);
        }
    }

    /** Points the submission at a newly stored file and counts the attempt. */
    public boolean replaceFile(Connection connection, long submissionId, String originalFilename, String storageKey,
                               long fileSizeBytes, Instant submittedAt, boolean late, int expectedVersion)
            throws SQLException {
        String sql = "UPDATE submissions SET original_filename = ?, storage_key = ?, file_size_bytes = ?, "
                + "submitted_at = ?, is_late = ?, attempt_number = attempt_number + 1, version = version + 1 "
                + "WHERE id = ? AND version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, originalFilename);
            statement.setString(2, storageKey);
            statement.setLong(3, fileSizeBytes);
            JdbcSupport.setInstant(statement, 4, submittedAt);
            statement.setBoolean(5, late);
            statement.setLong(6, submissionId);
            statement.setInt(7, expectedVersion);
            return statement.executeUpdate() == 1;
        }
    }

    public void updateLateFlag(Connection connection, long submissionId, boolean late) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE submissions SET is_late = ?, version = version + 1 WHERE id = ?")) {
            statement.setBoolean(1, late);
            statement.setLong(2, submissionId);
            statement.executeUpdate();
        }
    }

    public int countByAssignment(Connection connection, long assignmentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM submissions WHERE assignment_id = ?")) {
            statement.setLong(1, assignmentId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    /** Ungraded submissions from actively enrolled students in the instructor's active courses, oldest first. */
    public List<PendingSubmission> findAwaitingGrading(Connection connection, long instructorId, int limit)
            throws SQLException {
        String sql = """
                SELECT s.id, s.assignment_id, a.course_id, c.code, a.title, student.full_name,
                       s.submitted_at, s.is_late
                  FROM submissions s
                  JOIN assignments a ON a.id = s.assignment_id
                  JOIN courses c ON c.id = a.course_id
                  JOIN users student ON student.id = s.student_id
                  JOIN enrollments e ON e.course_id = c.id AND e.student_id = s.student_id AND e.status = 'ACTIVE'
                  LEFT JOIN grades g ON g.submission_id = s.id
                 WHERE c.instructor_id = ? AND c.status = 'ACTIVE' AND g.id IS NULL
                 ORDER BY s.submitted_at
                 LIMIT ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, instructorId);
            statement.setInt(2, limit);
            List<PendingSubmission> pending = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    pending.add(new PendingSubmission(
                            resultSet.getLong(1),
                            resultSet.getLong(2),
                            resultSet.getLong(3),
                            resultSet.getString(4),
                            resultSet.getString(5),
                            resultSet.getString(6),
                            JdbcSupport.getInstant(resultSet, "submitted_at"),
                            resultSet.getBoolean(8)));
                }
            }
            return pending;
        }
    }

    /** Total number of ungraded submissions for the instructor (the dashboard list is limited). */
    public int countAwaitingGrading(Connection connection, long instructorId) throws SQLException {
        String sql = """
                SELECT COUNT(*)
                  FROM submissions s
                  JOIN assignments a ON a.id = s.assignment_id
                  JOIN courses c ON c.id = a.course_id
                  JOIN enrollments e ON e.course_id = c.id AND e.student_id = s.student_id AND e.status = 'ACTIVE'
                  LEFT JOIN grades g ON g.submission_id = s.id
                 WHERE c.instructor_id = ? AND c.status = 'ACTIVE' AND g.id IS NULL
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, instructorId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private Optional<Submission> querySingle(PreparedStatement statement) throws SQLException {
        List<Submission> submissions = queryList(statement);
        return submissions.isEmpty() ? Optional.empty() : Optional.of(submissions.getFirst());
    }

    private List<Submission> queryList(PreparedStatement statement) throws SQLException {
        List<Submission> submissions = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                submissions.add(new Submission(
                        resultSet.getLong("id"),
                        resultSet.getLong("assignment_id"),
                        resultSet.getLong("student_id"),
                        resultSet.getString("student_name"),
                        resultSet.getString("original_filename"),
                        resultSet.getString("storage_key"),
                        resultSet.getLong("file_size_bytes"),
                        JdbcSupport.getInstant(resultSet, "submitted_at"),
                        resultSet.getBoolean("is_late"),
                        resultSet.getInt("attempt_number"),
                        resultSet.getInt("version")));
            }
        }
        return submissions;
    }
}
