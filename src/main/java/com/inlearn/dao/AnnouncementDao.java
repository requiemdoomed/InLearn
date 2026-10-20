package com.inlearn.dao;

import com.inlearn.model.Announcement;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC access to the {@code announcements} table.
 */
public class AnnouncementDao {

    private static final String ANNOUNCEMENT_SELECT = """
            SELECT n.id, n.course_id, n.author_id, n.title, n.body, n.created_at, n.updated_at, n.version,
                   c.code AS course_code, author.full_name AS author_name
              FROM announcements n
              JOIN courses c ON c.id = n.course_id
              JOIN users author ON author.id = n.author_id
            """;

    public Optional<Announcement> findById(Connection connection, long announcementId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ANNOUNCEMENT_SELECT + " WHERE n.id = ?")) {
            statement.setLong(1, announcementId);
            List<Announcement> announcements = queryList(statement);
            return announcements.isEmpty() ? Optional.empty() : Optional.of(announcements.getFirst());
        }
    }

    public List<Announcement> findByCourse(Connection connection, long courseId) throws SQLException {
        String sql = ANNOUNCEMENT_SELECT + " WHERE n.course_id = ? ORDER BY n.created_at DESC, n.id DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, courseId);
            return queryList(statement);
        }
    }

    /** Latest announcements from courses the student is actively enrolled in. */
    public List<Announcement> findRecentForStudent(Connection connection, long studentId, int limit)
            throws SQLException {
        String sql = ANNOUNCEMENT_SELECT + """
                 JOIN enrollments e ON e.course_id = n.course_id AND e.student_id = ? AND e.status = 'ACTIVE'
                ORDER BY n.created_at DESC, n.id DESC
                LIMIT ?
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentId);
            statement.setInt(2, limit);
            return queryList(statement);
        }
    }

    public long insert(Connection connection, long courseId, long authorId, String title, String body)
            throws SQLException {
        String sql = "INSERT INTO announcements (course_id, author_id, title, body) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, courseId);
            statement.setLong(2, authorId);
            statement.setString(3, title);
            statement.setString(4, body);
            statement.executeUpdate();
            return JdbcSupport.generatedKey(statement);
        }
    }

    /** @return {@code false} if the announcement changed since it was loaded */
    public boolean update(Connection connection, long announcementId, String title, String body, int expectedVersion)
            throws SQLException {
        String sql = "UPDATE announcements SET title = ?, body = ?, version = version + 1 WHERE id = ? AND version = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, title);
            statement.setString(2, body);
            statement.setLong(3, announcementId);
            statement.setInt(4, expectedVersion);
            return statement.executeUpdate() == 1;
        }
    }

    public void delete(Connection connection, long announcementId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM announcements WHERE id = ?")) {
            statement.setLong(1, announcementId);
            statement.executeUpdate();
        }
    }

    private List<Announcement> queryList(PreparedStatement statement) throws SQLException {
        List<Announcement> announcements = new ArrayList<>();
        try (ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                announcements.add(new Announcement(
                        resultSet.getLong("id"),
                        resultSet.getLong("course_id"),
                        resultSet.getString("course_code"),
                        resultSet.getLong("author_id"),
                        resultSet.getString("author_name"),
                        resultSet.getString("title"),
                        resultSet.getString("body"),
                        JdbcSupport.getInstant(resultSet, "created_at"),
                        JdbcSupport.getInstant(resultSet, "updated_at"),
                        resultSet.getInt("version")));
            }
        }
        return announcements;
    }
}
