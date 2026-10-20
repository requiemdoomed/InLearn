package com.inlearn.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Queries used only by the development demo-data seeder: checking whether the database is
 * empty and wiping it. Never used by the application itself.
 */
public class DevelopmentDataDao {

    /** Child tables first, so foreign keys never block a delete. */
    private static final List<String> TABLES_IN_DELETE_ORDER = List.of(
            "grades", "submissions", "assignments", "announcements", "course_materials", "enrollments", "courses", "users");

    public int countUsers(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM users");
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    /** Storage keys of every stored file, so the files can be removed along with the rows. */
    public List<String> findAllStorageKeys(Connection connection) throws SQLException {
        String sql = "SELECT storage_key FROM course_materials WHERE storage_key IS NOT NULL "
                + "UNION ALL SELECT storage_key FROM submissions";
        List<String> keys = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                keys.add(resultSet.getString(1));
            }
        }
        return keys;
    }

    /** Deletes every row from every InLearn table. Table names are constants, never user input. */
    public void deleteAllData(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String table : TABLES_IN_DELETE_ORDER) {
                statement.executeUpdate("DELETE FROM " + table);
            }
        }
    }
}
