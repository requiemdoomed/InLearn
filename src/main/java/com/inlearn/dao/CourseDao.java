package com.inlearn.dao;

import com.inlearn.model.Course;
import com.inlearn.model.CourseStatus;

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
 * JDBC access to the {@code courses} table. Queries return the instructor's name and the active
 * student count alongside each course so list screens need only one round trip.
 */
public class CourseDao {

    private static final String COURSE_SELECT = """
            SELECT c.id, c.code, c.title, c.description, c.term, c.instructor_id, c.status, c.version,
                   instructor.full_name AS instructor_name,
                   (SELECT COUNT(*) FROM enrollments e
                     WHERE e.course_id = c.id AND e.status = 'ACTIVE') AS active_student_count
              FROM courses c
              LEFT JOIN users instructor ON instructor.id = c.instructor_id
            """;

    public Optional<Course> findById(Connection connection, long courseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(COURSE_SELECT + " WHERE c.id = ?")) {
            statement.setLong(1, courseId);
            List<Course> courses = queryList(statement);
            return courses.isEmpty() ? Optional.empty() : Optional.of(courses.getFirst());
        }
    }

    public List<Course> findAll(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                COURSE_SELECT + " ORDER BY c.status, c.code")) {
            return queryList(statement);
        }
    }

    public List<Course> findActive(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                COURSE_SELECT + " WHERE c.status = 'ACTIVE' ORDER BY c.code")) {
            return queryList(statement);
        }
    }

    public List<Course> findByInstructor(Connection connection, long instructorId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                COURSE_SELECT + " WHERE c.instructor_id = ? ORDER BY c.status, c.code")) {
            statement.setLong(1, instructorId);
            return queryList(statement);
        }
    }

    /** Courses in which the student is currently enrolled (withdrawn enrollments excluded). */
    public List<Course> findForStudent(Connection connection, long studentId) throws SQLException {
        String sql = COURSE_SELECT
                + " JOIN enrollments mine ON mine.course_id = c.id AND mine.student_id = ? AND mine.status = 'ACTIVE'"
                + " ORDER BY c.status, c.code";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            return queryList(statement);
        }
    }

    public boolean codeInUse(Connection connection, String code, Long excludedCourseId) throws SQLException {
        String sql = "SELECT COUNT(*) FROM courses WHERE code = ? AND (? IS NULL OR id <> ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            JdbcSupport.setNullableLong(statement, 2, excludedCourseId);
            JdbcSupport.setNullableLong(statement, 3, excludedCourseId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1) > 0;
            }
        }
    }

    public long insert(Connection connection, String code, String title, String description, String term,
                       Long instructorId) throws SQLException {
        String sql = "INSERT INTO courses (code, title, description, term, instructor_id, status) "
                + "VALUES (?, ?, ?, ?, ?, 'ACTIVE')";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, code);
            statement.setString(2, title);
            JdbcSupport.setNullableString(statement, 3, description);
            statement.setString(4, term);
            JdbcSupport.setNullableLong(statement, 5, instructorId);
            statement.executeUpdate();
            return JdbcSupport.generatedKey(statement);
        }
    }

    /** @return {@code false} if the course was changed by someone else since it was loaded */
    public boolean update(Connection connection, long courseId, String code, String title, String description,
                          String term, Long instructorId, int expectedVersion) throws SQLException {
        String sql = "UPDATE courses SET code = ?, title = ?, description = ?, term = ?, instructor_id = ?, "
                + "version = version + 1 WHERE id = ? AND version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            statement.setString(2, title);
            JdbcSupport.setNullableString(statement, 3, description);
            statement.setString(4, term);
            JdbcSupport.setNullableLong(statement, 5, instructorId);
            statement.setLong(6, courseId);
            statement.setInt(7, expectedVersion);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean updateDescription(Connection connection, long courseId, String description, int expectedVersion)
            throws SQLException {
        String sql = "UPDATE courses SET description = ?, version = version + 1 WHERE id = ? AND version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            JdbcSupport.setNullableString(statement, 1, description);
            statement.setLong(2, courseId);
            statement.setInt(3, expectedVersion);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean updateStatus(Connection connection, long courseId, CourseStatus status, Instant archivedAt,
                                int expectedVersion) throws SQLException {
        String sql = "UPDATE courses SET status = ?, archived_at = ?, version = version + 1 "
                + "WHERE id = ? AND version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            JdbcSupport.setInstant(statement, 2, archivedAt);
            statement.setLong(3, courseId);
            statement.setInt(4, expectedVersion);
            return statement.executeUpdate() == 1;
        }
    }

    /** Number of rows in other tables that belong to the course; a course can be deleted only when this is 0. */
    public int countDependentRecords(Connection connection, long courseId) throws SQLException {
        String sql = """
                SELECT (SELECT COUNT(*) FROM enrollments WHERE course_id = ?)
                     + (SELECT COUNT(*) FROM assignments WHERE course_id = ?)
                     + (SELECT COUNT(*) FROM course_materials WHERE course_id = ?)
                     + (SELECT COUNT(*) FROM announcements WHERE course_id = ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 1; index <= 4; index++) {
                statement.setLong(index, courseId);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    public void delete(Connection connection, long courseId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM courses WHERE id = ?")) {
            statement.setLong(1, courseId);
            statement.executeUpdate();
        }
    }

    public int countActive(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM courses WHERE status = 'ACTIVE'");
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private List<Course> queryList(PreparedStatement statement) throws SQLException {
        List<Course> courses = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                courses.add(new Course(
                        resultSet.getLong("id"),
                        resultSet.getString("code"),
                        resultSet.getString("title"),
                        resultSet.getString("description"),
                        resultSet.getString("term"),
                        JdbcSupport.getNullableLong(resultSet, "instructor_id"),
                        resultSet.getString("instructor_name"),
                        CourseStatus.valueOf(resultSet.getString("status")),
                        resultSet.getInt("active_student_count"),
                        resultSet.getInt("version")));
            }
        }
        return courses;
    }
}
