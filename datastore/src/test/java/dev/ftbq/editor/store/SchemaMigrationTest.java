package dev.ftbq.editor.store;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaMigrationTest {

    @Test
    void appliesChapterQuestSchemaMigration() throws Exception {
        try (Connection connection = Jdbc.openInMemory()) {
            assertTrue(tableExists(connection, "chapters"), "chapters table should exist");
            assertTrue(columnExists(connection, "chapters", "icon"), "chapters table should include icon column");
            assertTrue(tableExists(connection, "chapter_quests"), "chapter_quests table should exist");
            assertTrue(tableExists(connection, "quests"), "quests table should exist");
            assertTrue(columnExists(connection, "quests", "chapter_id"), "quests table should have chapter_id column");
            assertTrue(tableExists(connection, "quest_details"), "legacy quest details table should exist");
            assertTrue(migrationRecorded(connection, "001_create_quest_schema.sql"), "initial migration marker should be recorded");
            assertTrue(migrationRecorded(connection, "002_add_chapter_tables.sql"), "chapter migration marker should be recorded");
        }
    }

    private boolean tableExists(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?"
        )) {
            statement.setString(1, table);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean columnExists(Connection connection, String table, String column) throws SQLException {
        String pragmaSql = "PRAGMA table_info('" + table + "')";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(pragmaSql)) {
            while (resultSet.next()) {
                if (column.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private boolean migrationRecorded(Connection connection, String migration) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM schema_migrations WHERE name = ?"
        )) {
            statement.setString(1, migration);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }
}
