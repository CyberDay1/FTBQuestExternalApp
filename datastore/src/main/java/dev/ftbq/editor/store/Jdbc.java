package dev.ftbq.editor.store;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public final class Jdbc {
    private static final String SCHEMA_RESOURCE = "/dev/ftbq/editor/store/schema.sql";

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
            try (Statement pragma = connection.createStatement()) {
                pragma.execute("PRAGMA foreign_keys = ON");
            }
            for (String statementSql : loadSchemaStatements()) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(statementSql);
                }
            }
            connection.commit();
        } catch (SQLException | IOException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private static List<String> loadSchemaStatements() throws IOException {
        try (InputStream stream = Jdbc.class.getResourceAsStream(SCHEMA_RESOURCE)) {
            if (stream == null) {
                throw new IOException("Schema resource not found: " + SCHEMA_RESOURCE);
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
}
