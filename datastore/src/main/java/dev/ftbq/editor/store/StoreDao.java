package dev.ftbq.editor.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.ftbq.editor.domain.AdvancementTask;
import dev.ftbq.editor.domain.CommandReward;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.ItemReward;
import dev.ftbq.editor.domain.ItemTask;
import dev.ftbq.editor.domain.LocationTask;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.Reward;
import dev.ftbq.editor.domain.Task;
import dev.ftbq.editor.domain.XpReward;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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

    private static final String UPSERT_QUEST_SQL = """
            INSERT INTO quests (id, title, description, icon, icon_relative_path, visibility)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(id) DO UPDATE SET
                title = excluded.title,
                description = excluded.description,
                icon = excluded.icon,
                icon_relative_path = excluded.icon_relative_path,
                visibility = excluded.visibility
            """;

    private static final String DELETE_QUEST_TASKS_SQL = "DELETE FROM quest_tasks WHERE quest_id = ?";
    private static final String DELETE_QUEST_REWARDS_SQL = "DELETE FROM quest_rewards WHERE quest_id = ?";
    private static final String DELETE_QUEST_DEPENDENCIES_SQL = "DELETE FROM quest_dependencies WHERE quest_id = ?";

    private static final String INSERT_QUEST_TASK_SQL = """
            INSERT INTO quest_tasks (quest_id, task_index, type, item_id, item_count, consume, advancement_id, dimension, x, y, z, radius)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_QUEST_REWARD_SQL = """
            INSERT INTO quest_rewards (quest_id, reward_index, type, item_id, item_count, amount, command, as_player, metadata)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_QUEST_DEPENDENCY_SQL = """
            INSERT INTO quest_dependencies (quest_id, dependency_quest_id, required)
            VALUES (?, ?, ?)
            """;

    private static final ObjectMapper METADATA_MAPPER;

    static {
        METADATA_MAPPER = new ObjectMapper();
        METADATA_MAPPER.registerModule(new Jdk8Module());
        METADATA_MAPPER.registerModule(new JavaTimeModule());
    }

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

    public List<ItemEntity> listItems(
            String filterText,
            List<String> tagFilters,
            String modFilter,
            String version,
            String kind,
            SortMode sortMode,
            int limit,
            int offset) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, display_name, is_vanilla, mod_id, mod_name, tags, texture_path, icon_hash, source_jar, version, kind
                FROM items
                """);

        List<String> conditions = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();

        if (filterText != null && !filterText.isBlank()) {
            String normalized = "%" + filterText.toLowerCase(Locale.ROOT) + "%";
            conditions.add("(LOWER(COALESCE(display_name, '')) LIKE ? OR LOWER(id) LIKE ?)");
            parameters.add(normalized);
            parameters.add(normalized);
        }

        if (tagFilters != null) {
            for (String tag : tagFilters) {
                if (tag == null || tag.isBlank()) {
                    continue;
                }
                conditions.add("(tags IS NOT NULL AND LOWER(tags) LIKE ?)");
                parameters.add("%\"" + tag.toLowerCase(Locale.ROOT) + "\"%");
            }
        }

        if (modFilter != null && !modFilter.isBlank()) {
            String normalizedMod = modFilter.toLowerCase(Locale.ROOT);
            conditions.add("(LOWER(COALESCE(mod_id, '')) = ? OR LOWER(COALESCE(mod_name, '')) = ?)");
            parameters.add(normalizedMod);
            parameters.add(normalizedMod);
        }

        if (version != null && !version.isBlank()) {
            conditions.add("version = ?");
            parameters.add(version);
        }

        if (kind != null && !kind.isBlank()) {
            conditions.add("kind = ?");
            parameters.add(kind);
        }

        if (!conditions.isEmpty()) {
            sql.append(" WHERE ");
            sql.append(String.join(" AND ", conditions));
        }

        sql.append(" ORDER BY ");
        SortMode effectiveSortMode = sortMode == null ? SortMode.NAME : sortMode;
        switch (effectiveSortMode) {
            case MOD -> sql.append("LOWER(COALESCE(mod_name, mod_id, '')) ASC, LOWER(COALESCE(display_name, id)) ASC, id ASC");
            case VANILLA_FIRST -> sql.append("is_vanilla DESC, LOWER(COALESCE(display_name, id)) ASC, id ASC");
            case NAME -> sql.append("LOWER(COALESCE(display_name, id)) ASC, id ASC");
        }

        sql.append(" LIMIT ? OFFSET ?");
        parameters.add(limit);
        parameters.add(offset);

        try (PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            int index = 1;
            for (Object parameter : parameters) {
                if (parameter instanceof String value) {
                    statement.setString(index++, value);
                } else if (parameter instanceof Integer value) {
                    statement.setInt(index++, value);
                } else {
                    throw new IllegalStateException("Unsupported parameter type: " + parameter.getClass());
                }
            }

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

    public void saveQuest(Quest quest) {
        Objects.requireNonNull(quest, "quest");
        boolean previousAutoCommit;
        try {
            previousAutoCommit = connection.getAutoCommit();
        } catch (SQLException e) {
            throw new UncheckedSqlException("Failed to determine auto-commit state", e);
        }

        try {
            connection.setAutoCommit(false);
            upsertQuestRow(quest);
            deleteQuestChildren(quest.id());
            insertQuestTasks(quest);
            insertQuestRewards(quest);
            insertQuestDependencies(quest);
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                e.addSuppressed(rollbackException);
            }
            throw new UncheckedSqlException("Failed to save quest " + quest.id(), e);
        } finally {
            try {
                connection.setAutoCommit(previousAutoCommit);
            } catch (SQLException e) {
                throw new UncheckedSqlException("Failed to restore auto-commit state", e);
            }
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

    private void upsertQuestRow(Quest quest) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_QUEST_SQL)) {
            statement.setString(1, quest.id());
            statement.setString(2, quest.title());
            statement.setString(3, quest.description());
            statement.setString(4, quest.icon().icon());
            setStringOrNull(statement, 5, quest.icon().relativePath().orElse(null));
            statement.setString(6, quest.visibility().name());
            statement.executeUpdate();
        }
    }

    private void deleteQuestChildren(String questId) throws SQLException {
        deleteRecords(DELETE_QUEST_TASKS_SQL, questId);
        deleteRecords(DELETE_QUEST_REWARDS_SQL, questId);
        deleteRecords(DELETE_QUEST_DEPENDENCIES_SQL, questId);
    }

    private void deleteRecords(String sql, String questId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, questId);
            statement.executeUpdate();
        }
    }

    private void insertQuestTasks(Quest quest) throws SQLException {
        if (quest.tasks().isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_QUEST_TASK_SQL)) {
            int index = 0;
            for (Task task : quest.tasks()) {
                statement.setString(1, quest.id());
                statement.setInt(2, index++);
                statement.setString(3, task.type());
                if (task instanceof ItemTask itemTask) {
                    ItemRef item = itemTask.item();
                    statement.setString(4, item.itemId());
                    statement.setInt(5, item.count());
                    statement.setInt(6, itemTask.consume() ? 1 : 0);
                } else {
                    setStringOrNull(statement, 4, null);
                    setIntegerOrNull(statement, 5, null);
                    setIntegerOrNull(statement, 6, null);
                }

                if (task instanceof AdvancementTask advancementTask) {
                    statement.setString(7, advancementTask.advancementId());
                } else {
                    setStringOrNull(statement, 7, null);
                }

                if (task instanceof LocationTask locationTask) {
                    statement.setString(8, locationTask.dimension());
                    statement.setDouble(9, locationTask.x());
                    statement.setDouble(10, locationTask.y());
                    statement.setDouble(11, locationTask.z());
                    statement.setDouble(12, locationTask.radius());
                } else {
                    setStringOrNull(statement, 8, null);
                    setDoubleOrNull(statement, 9, null);
                    setDoubleOrNull(statement, 10, null);
                    setDoubleOrNull(statement, 11, null);
                    setDoubleOrNull(statement, 12, null);
                }

                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertQuestRewards(Quest quest) throws SQLException {
        if (quest.rewards().isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_QUEST_REWARD_SQL)) {
            int index = 0;
            for (Reward reward : quest.rewards()) {
                statement.setString(1, quest.id());
                statement.setInt(2, index++);
                statement.setString(3, reward.type());

                if (reward instanceof ItemReward itemReward) {
                    ItemRef item = itemReward.item();
                    statement.setString(4, item.itemId());
                    statement.setInt(5, item.count());
                } else {
                    setStringOrNull(statement, 4, null);
                    setIntegerOrNull(statement, 5, null);
                }

                if (reward instanceof XpReward xpReward) {
                    statement.setInt(6, xpReward.amount());
                } else {
                    setIntegerOrNull(statement, 6, null);
                }

                if (reward instanceof CommandReward commandReward) {
                    statement.setString(7, commandReward.command());
                    statement.setInt(8, commandReward.asPlayer() ? 1 : 0);
                } else {
                    setStringOrNull(statement, 7, null);
                    setIntegerOrNull(statement, 8, null);
                }

                String metadataJson = serializeMetadata(reward.metadata());
                setStringOrNull(statement, 9, metadataJson);

                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertQuestDependencies(Quest quest) throws SQLException {
        if (quest.dependencies().isEmpty()) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement(INSERT_QUEST_DEPENDENCY_SQL)) {
            for (Dependency dependency : quest.dependencies()) {
                statement.setString(1, quest.id());
                statement.setString(2, dependency.questId());
                statement.setInt(3, dependency.required() ? 1 : 0);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private static void setIntegerOrNull(PreparedStatement statement, int index, Integer value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
        } else {
            statement.setInt(index, value);
        }
    }

    private static void setDoubleOrNull(PreparedStatement statement, int index, Double value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.REAL);
        } else {
            statement.setDouble(index, value);
        }
    }

    private static String serializeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return METADATA_MAPPER.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            throw new UncheckedSqlException("Failed to serialize reward metadata", e);
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

    public enum SortMode {
        NAME,
        MOD,
        VANILLA_FIRST
    }
}
