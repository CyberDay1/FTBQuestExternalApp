package dev.ftbq.editor.store;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class Jdbc {
    private static final String SCHEMA_RESOURCE = "/dev/ftbq/editor/store/schema.sql";
    private static final String MIGRATIONS_BASE = "/dev/ftbq/editor/store/migrations/";
    private static final List<String> MIGRATIONS = List.of(
            "001_create_quest_schema.sql"
    );

    private Jdbc() {
    }

    public static Connection open(Path databasePath) {
        try {
            Path absolutePath = databasePath.toAbsolutePath();
            Path parent = absolutePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + absolutePath);
            applySchema(connection);
            return connection;
        } catch (SQLException | IOException e) {
            throw new UncheckedSqlException("Failed to open SQLite database at " + databasePath, e);
        }
    }

    public static Connection openInMemory() {
        try {
            Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
            applySchema(connection);
            return connection;
        } catch (SQLException | IOException e) {
            throw new UncheckedSqlException("Failed to open in-memory SQLite database", e);
        }
    }

    private static void applySchema(Connection connection) throws SQLException, IOException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            enableForeignKeys(connection);
            renameLegacyQuestTable(connection);
            applyStatements(connection, loadSchemaStatements());
            ensureSchemaMigrationsTable(connection);
            applyMigrations(connection);
            connection.commit();
        } catch (SQLException | IOException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static void enableForeignKeys(Connection connection) throws SQLException {
        try (Statement pragma = connection.createStatement()) {
            pragma.execute("PRAGMA foreign_keys = ON");
        }
    }

    private static void renameLegacyQuestTable(Connection connection) throws SQLException {
        if (!tableExists(connection, "quests")) {
            return;
        }
        if (tableExists(connection, "quest_details")) {
            return;
        }
        if (!columnExists(connection, "quests", "description")) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE quests RENAME TO quest_details");
        }
    }

    private static void ensureSchemaMigrationsTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_migrations (
                        name TEXT PRIMARY KEY,
                        applied_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
        }
    }

    private static void applyStatements(Connection connection, List<String> statements) throws SQLException {
        for (String statementSql : statements) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(statementSql);
            }
        }
    }

    private static void applyMigrations(Connection connection) throws SQLException, IOException {
        for (String migration : MIGRATIONS) {
            if (isMigrationApplied(connection, migration)) {
                continue;
            }
            List<String> statements = loadStatements(MIGRATIONS_BASE + migration);
            applyStatements(connection, statements);
            markMigrationApplied(connection, migration);
        }
    }

    private static boolean isMigrationApplied(Connection connection, String migration) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM schema_migrations WHERE name = ?"
        )) {
            statement.setString(1, migration);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static void markMigrationApplied(Connection connection, String migration) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO schema_migrations (name) VALUES (?)"
        )) {
            statement.setString(1, migration);
            statement.executeUpdate();
        }
    }

    private static List<String> loadSchemaStatements() throws IOException {
        return loadStatements(SCHEMA_RESOURCE);
    }

    private static List<String> loadStatements(String resourcePath) throws IOException {
        try (InputStream stream = Jdbc.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Schema resource not found: " + resourcePath);
            }
            String schema = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            String[] fragments = schema.replace("\r", "").split(";");
            List<String> statements = new ArrayList<>();
            for (String fragment : fragments) {
                String trimmed = fragment.trim();
                if (!trimmed.isEmpty()) {
                    statements.add(trimmed);
                }
            }
            return statements;
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?"
        )) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        String pragmaSql = "PRAGMA table_info('" + tableName + "')";
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(pragmaSql)) {
            while (resultSet.next()) {
                String name = resultSet.getString("name");
                if (columnName.equalsIgnoreCase(name)) {
                    return true;
                }
            }
            return false;
        }
    }
}
