package dev.ftbq.editor.store;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class StoreDao {
    private static final String UPSERT_ITEM_SQL = """
            INSERT INTO items (id, display_name, is_vanilla, mod_id, mod_name, tags, texture_path, icon_hash, source_jar, version, kind)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                display_name = excluded.display_name,
                is_vanilla = excluded.is_vanilla,
                mod_id = excluded.mod_id,
                mod_name = excluded.mod_name,
                tags = excluded.tags,
                texture_path = excluded.texture_path,
                icon_hash = excluded.icon_hash,
                source_jar = excluded.source_jar,
                version = excluded.version,
                kind = excluded.kind
            """;

    private static final String UPSERT_LOOT_TABLE_SQL = """
            INSERT INTO loot_tables (name, data)
            VALUES (?, ?)
            ON CONFLICT(name) DO UPDATE SET data = excluded.data
            """;

    private static final String UPSERT_SETTING_SQL = """
            INSERT INTO settings (key, value)
            VALUES (?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
            """;

    private final Connection connection;

    public StoreDao(Connection connection) {
        this.connection = connection;
    }

    public void upsertItem(ItemEntity item) {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_ITEM_SQL)) {
            statement.setString(1, item.id());
            statement.setString(2, item.displayName());
            statement.setInt(3, item.isVanilla() ? 1 : 0);
            setStringOrNull(statement, 4, item.modId());
            setStringOrNull(statement, 5, item.modName());
            setStringOrNull(statement, 6, item.tags());
            setStringOrNull(statement, 7, item.texturePath());
            setStringOrNull(statement, 8, item.iconHash());
            setStringOrNull(statement, 9, item.sourceJar());
            setStringOrNull(statement, 10, item.version());
            setStringOrNull(statement, 11, item.kind());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new UncheckedSqlException("Failed to upsert item " + item.id(), e);
        }
    }

    public List<ItemEntity> listItems() {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, display_name, is_vanilla, mod_id, mod_name, tags, texture_path, icon_hash, source_jar, version, kind
                FROM items
                ORDER BY id
                """)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ItemEntity> items = new ArrayList<>();
                while (resultSet.next()) {
                    items.add(mapItem(resultSet));
                }
                return items;
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException("Failed to list items", e);
        }
    }

    public Optional<ItemEntity> findItemById(String id) {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, display_name, is_vanilla, mod_id, mod_name, tags, texture_path, icon_hash, source_jar, version, kind
                FROM items
                WHERE id = ?
                """)) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapItem(resultSet));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException("Failed to load item " + id, e);
        }
    }

    public void upsertLootTable(LootTableEntity lootTable) {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_LOOT_TABLE_SQL)) {
            statement.setString(1, lootTable.name());
            statement.setString(2, lootTable.data());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new UncheckedSqlException("Failed to upsert loot table " + lootTable.name(), e);
        }
    }

    public List<LootTableEntity> listLootTables() {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT name, data
                FROM loot_tables
                ORDER BY name
                """)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                List<LootTableEntity> tables = new ArrayList<>();
                while (resultSet.next()) {
                    tables.add(new LootTableEntity(resultSet.getString("name"), resultSet.getString("data")));
                }
                return tables;
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException("Failed to list loot tables", e);
        }
    }

    public Optional<LootTableEntity> findLootTable(String name) {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT name, data
                FROM loot_tables
                WHERE name = ?
                """)) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(new LootTableEntity(resultSet.getString("name"), resultSet.getString("data")));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException("Failed to load loot table " + name, e);
        }
    }

    public void setSetting(String key, String value) {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_SETTING_SQL)) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new UncheckedSqlException("Failed to store setting " + key, e);
        }
    }

    public Optional<String> getSetting(String key) {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT value
                FROM settings
                WHERE key = ?
                """)) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.ofNullable(resultSet.getString("value"));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException("Failed to load setting " + key, e);
        }
    }

    private static ItemEntity mapItem(ResultSet resultSet) throws SQLException {
        return new ItemEntity(
                resultSet.getString("id"),
                resultSet.getString("display_name"),
                resultSet.getInt("is_vanilla") != 0,
                resultSet.getString("mod_id"),
                resultSet.getString("mod_name"),
                resultSet.getString("tags"),
                resultSet.getString("texture_path"),
                resultSet.getString("icon_hash"),
                resultSet.getString("source_jar"),
                resultSet.getString("version"),
                resultSet.getString("kind"));
    }

    private static void setStringOrNull(PreparedStatement statement, int index, String value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.VARCHAR);
        } else {
            statement.setString(index, value);
        }
    }

    public record ItemEntity(
            String id,
            String displayName,
            boolean isVanilla,
            String modId,
            String modName,
            String tags,
            String texturePath,
            String iconHash,
            String sourceJar,
            String version,
            String kind) {
    }

    public record LootTableEntity(String name, String data) {
    }
}
