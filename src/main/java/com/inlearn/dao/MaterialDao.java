package com.inlearn.dao;

import com.inlearn.model.CourseMaterial;
import com.inlearn.model.MaterialType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC access to the {@code course_materials} table.
 */
public class MaterialDao {

    private static final String MATERIAL_SELECT = """
            SELECT m.id, m.course_id, m.title, m.description, m.material_type, m.link_url,
                   m.original_filename, m.storage_key, m.file_size_bytes, m.created_at,
                   u.full_name AS uploaded_by_name
              FROM course_materials m
              JOIN users u ON u.id = m.uploaded_by
            """;

    public List<CourseMaterial> findByCourse(Connection connection, long courseId) throws SQLException {
        String sql = MATERIAL_SELECT + " WHERE m.course_id = ? ORDER BY m.created_at DESC, m.id DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, courseId);
            List<CourseMaterial> materials = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    materials.add(map(resultSet));
                }
            }
            return materials;
        }
    }

    public Optional<CourseMaterial> findById(Connection connection, long materialId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(MATERIAL_SELECT + " WHERE m.id = ?")) {
            statement.setLong(1, materialId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(map(resultSet)) : Optional.empty();
            }
        }
    }

    public long insertFile(Connection connection, long courseId, String title, String description,
                           String originalFilename, String storageKey, long fileSizeBytes, long uploadedBy)
            throws SQLException {
        String sql = "INSERT INTO course_materials (course_id, title, description, material_type, "
                + "original_filename, storage_key, file_size_bytes, uploaded_by) VALUES (?, ?, ?, 'FILE', ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, courseId);
            statement.setString(2, title);
            JdbcSupport.setNullableString(statement, 3, description);
            statement.setString(4, originalFilename);
            statement.setString(5, storageKey);
            statement.setLong(6, fileSizeBytes);
            statement.setLong(7, uploadedBy);
            statement.executeUpdate();
            return JdbcSupport.generatedKey(statement);
        }
    }

    public long insertLink(Connection connection, long courseId, String title, String description, String linkUrl,
                           long uploadedBy) throws SQLException {
        String sql = "INSERT INTO course_materials (course_id, title, description, material_type, link_url, "
                + "uploaded_by) VALUES (?, ?, ?, 'LINK', ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setLong(1, courseId);
            statement.setString(2, title);
            JdbcSupport.setNullableString(statement, 3, description);
            statement.setString(4, linkUrl);
            statement.setLong(5, uploadedBy);
            statement.executeUpdate();
            return JdbcSupport.generatedKey(statement);
        }
    }

    public void delete(Connection connection, long materialId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM course_materials WHERE id = ?")) {
            statement.setLong(1, materialId);
            statement.executeUpdate();
        }
    }

    private CourseMaterial map(ResultSet resultSet) throws SQLException {
        return new CourseMaterial(
                resultSet.getLong("id"),
                resultSet.getLong("course_id"),
                resultSet.getString("title"),
                resultSet.getString("description"),
                MaterialType.valueOf(resultSet.getString("material_type")),
                resultSet.getString("link_url"),
                resultSet.getString("original_filename"),
                resultSet.getString("storage_key"),
                JdbcSupport.getNullableLong(resultSet, "file_size_bytes"),
                resultSet.getString("uploaded_by_name"),
                JdbcSupport.getInstant(resultSet, "created_at"));
    }
}
