package dev.ftbq.editor.store;

import dev.ftbq.editor.domain.AdvancementTask;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.ItemTask;
import dev.ftbq.editor.domain.LocationTask;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.Reward;
import dev.ftbq.editor.domain.RewardCommand;
import dev.ftbq.editor.domain.RewardType;
import dev.ftbq.editor.domain.Task;
import dev.ftbq.editor.domain.Visibility;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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

    private static final String UPSERT_QUEST_POSITION_SQL = """
            INSERT INTO quest_positions (quest_id, x, y)
            VALUES (?, ?, ?)
            ON CONFLICT(quest_id) DO UPDATE SET
                x = excluded.x,
                y = excluded.y
            """;

    private static final String SELECT_QUEST_POSITION_SQL = """
            SELECT quest_id, x, y
            FROM quest_positions
            WHERE quest_id = ?
            """;

    private static final String DELETE_QUEST_TASKS_SQL = "DELETE FROM quest_tasks WHERE quest_id = ?";
    private static final String DELETE_QUEST_REWARDS_SQL = "DELETE FROM quest_rewards WHERE quest_id = ?";
    private static final String DELETE_QUEST_DEPENDENCIES_SQL = "DELETE FROM quest_dependencies WHERE quest_id = ?";
    private static final String DELETE_DEPENDENCY_REFERENCES_SQL = "DELETE FROM quest_dependencies WHERE dependency_quest_id = ?";
    private static final String DELETE_QUEST_SQL = "DELETE FROM quests WHERE id = ?";
    private static final String DELETE_QUEST_POSITION_SQL = "DELETE FROM quest_positions WHERE quest_id = ?";

    private static final String INSERT_QUEST_TASK_SQL = """
            INSERT INTO quest_tasks (quest_id, task_index, type, item_id, item_count, consume, advancement_id, dimension, x, y, z, radius)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_QUEST_REWARD_SQL = """
            INSERT INTO quest_rewards (quest_id, reward_index, type, item_id, item_count, loot_table_id, experience, command, run_as_server)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String INSERT_QUEST_DEPENDENCY_SQL = """
            INSERT INTO quest_dependencies (quest_id, dependency_quest_id, required)
            VALUES (?, ?, ?)
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

    /**
     * Appends all items currently stored in the database to the supplied map, keyed by item id.
     * Existing entries are preserved unless a matching id is encountered, in which case the
     * database row overwrites the previous value.
     *
     * @param existingItems map to mutate with database contents
     * @return the provided map instance for chaining
     */
    public Map<String, ItemEntity> appendItems(Map<String, ItemEntity> existingItems) {
        Objects.requireNonNull(existingItems, "existingItems");

        Map<String, ItemEntity> newItems = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, display_name, is_vanilla, mod_id, mod_name, tags, texture_path, icon_hash, source_jar, version, kind
                FROM items
                ORDER BY id
                """)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    ItemEntity entity = mapItem(resultSet);
                    newItems.put(entity.id(), entity);
                }
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException("Failed to append catalog items", e);
        }

        existingItems.putAll(newItems);
        return existingItems;
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

    /**
     * Lists all quests from the database. Only basic quest fields are loaded
     * (id, title, description, icon and visibility). Tasks, rewards and dependencies
     * are not loaded by this method. Use this to populate dependency selectors in the UI.
     *
     * @return list of all quests ordered by id
     */
    public List<Quest> listQuests() {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, title, description, icon, icon_relative_path, visibility FROM quests ORDER BY id"
        )) {
            List<Quest> quests = new ArrayList<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String id = resultSet.getString("id");
                    String title = resultSet.getString("title");
                    String description = resultSet.getString("description");
                    String iconId = resultSet.getString("icon");
                    String iconRelPath = resultSet.getString("icon_relative_path");
                    IconRef icon = new IconRef(iconId, Optional.ofNullable(iconRelPath));
                    Visibility visibility = Visibility.valueOf(resultSet.getString("visibility"));
                    quests.add(Quest.builder()
                            .id(id)
                            .title(title)
                            .description(description)
                            .icon(icon)
                            .visibility(visibility)
                            .build());
                }
            }
            return quests;
        } catch (SQLException e) {
            throw new UncheckedSqlException("Failed to list quests", e);
        }
    }

    public Optional<Quest> findQuestById(String questId) {
        Objects.requireNonNull(questId, "questId");
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, title, description, icon, icon_relative_path, visibility
                FROM quests
                WHERE id = ?
                """)) {
            statement.setString(1, questId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                String id = resultSet.getString("id");
                String title = resultSet.getString("title");
                String description = resultSet.getString("description");
                String iconId = resultSet.getString("icon");
                String iconRelativePath = resultSet.getString("icon_relative_path");
                Visibility visibility = Visibility.valueOf(resultSet.getString("visibility"));

                List<Task> tasks = loadQuestTasks(id);
                List<Reward> rewards = loadQuestRewards(id);
                List<Dependency> dependencies = loadQuestDependencies(id);

                Quest quest = Quest.builder()
                        .id(id)
                        .title(title)
                        .description(description)
                        .icon(new IconRef(iconId, Optional.ofNullable(iconRelativePath)))
                        .visibility(visibility)
                        .tasks(tasks)
                        .rewards(rewards)
                        .dependencies(dependencies)
                        .build();
                return Optional.of(quest);
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException("Failed to load quest " + questId, e);
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

    public void saveQuestPosition(String questId, double x, double y) {
        Objects.requireNonNull(questId, "questId");
        try (PreparedStatement statement = connection.prepareStatement(UPSERT_QUEST_POSITION_SQL)) {
            statement.setString(1, questId);
            statement.setDouble(2, x);
            statement.setDouble(3, y);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new UncheckedSqlException("Failed to save quest position for " + questId, e);
        }
    }

    public Optional<QuestPosition> findQuestPosition(String questId) {
        Objects.requireNonNull(questId, "questId");
        try (PreparedStatement statement = connection.prepareStatement(SELECT_QUEST_POSITION_SQL)) {
            statement.setString(1, questId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                double x = resultSet.getDouble("x");
                double y = resultSet.getDouble("y");
                return Optional.of(new QuestPosition(resultSet.getString("quest_id"), x, y));
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException("Failed to load quest position for " + questId, e);
        }
    }

    public Map<String, QuestPosition> findQuestPositions(Collection<String> questIds) {
        Objects.requireNonNull(questIds, "questIds");
        List<String> filteredIds = questIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (filteredIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = filteredIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        String sql = "SELECT quest_id, x, y FROM quest_positions WHERE quest_id IN (" + placeholders + ")";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            for (String questId : filteredIds) {
                statement.setString(index++, questId);
            }
            Map<String, QuestPosition> positions = new HashMap<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String questId = resultSet.getString("quest_id");
                    double x = resultSet.getDouble("x");
                    double y = resultSet.getDouble("y");
                    positions.put(questId, new QuestPosition(questId, x, y));
                }
            }
            return positions;
        } catch (SQLException e) {
            throw new UncheckedSqlException("Failed to load quest positions", e);
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

    public void deleteQuest(String questId) {
        Objects.requireNonNull(questId, "questId");
        boolean previousAutoCommit;
        try {
            previousAutoCommit = connection.getAutoCommit();
        } catch (SQLException e) {
            throw new UncheckedSqlException("Failed to determine auto-commit state", e);
        }

        try {
            connection.setAutoCommit(false);
            deleteRecords(DELETE_QUEST_TASKS_SQL, questId);
            deleteRecords(DELETE_QUEST_REWARDS_SQL, questId);
            deleteRecords(DELETE_QUEST_DEPENDENCIES_SQL, questId);
            deleteRecords(DELETE_DEPENDENCY_REFERENCES_SQL, questId);
            deleteRecords(DELETE_QUEST_POSITION_SQL, questId);
            try (PreparedStatement statement = connection.prepareStatement(DELETE_QUEST_SQL)) {
                statement.setString(1, questId);
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackException) {
                e.addSuppressed(rollbackException);
            }
            throw new UncheckedSqlException("Failed to delete quest " + questId, e);
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

    /**
     * Loads a quest header and its dependency list by quest ID.
     * Tasks and rewards are not loaded by this method.
     *
     * @param id quest identifier to look up
     * @return the quest if present, otherwise an empty optional
     */
    public Optional<Quest> findQuestHeaderById(String id) {
        Objects.requireNonNull(id, "id");
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, title, description, icon, icon_relative_path, visibility
                FROM quests
                WHERE id = ?
                """)) {
            statement.setString(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                String questId = resultSet.getString("id");
                String title = resultSet.getString("title");
                String description = resultSet.getString("description");
                String iconId = resultSet.getString("icon");
                String iconRelativePath = resultSet.getString("icon_relative_path");
                IconRef icon = new IconRef(iconId, Optional.ofNullable(iconRelativePath));
                Visibility visibility = Visibility.valueOf(resultSet.getString("visibility"));

                List<Dependency> dependencies = new ArrayList<>();
                try (PreparedStatement depStatement = connection.prepareStatement("""
                        SELECT dependency_quest_id, required
                        FROM quest_dependencies
                        WHERE quest_id = ?
                        """)) {
                    depStatement.setString(1, questId);
                    try (ResultSet depResultSet = depStatement.executeQuery()) {
                        while (depResultSet.next()) {
                            String dependencyId = depResultSet.getString("dependency_quest_id");
                            boolean required = depResultSet.getInt("required") != 0;
                            dependencies.add(new Dependency(dependencyId, required));
                        }
                    }
                }

                return Optional.of(Quest.builder()
                        .id(questId)
                        .title(title)
                        .description(description)
                        .icon(icon)
                        .visibility(visibility)
                        .dependencies(dependencies)
                        .tasks(List.of())
                        .rewards(List.of())
                        .build());
            }
        } catch (SQLException e) {
            throw new UncheckedSqlException("Failed to load quest " + id, e);
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

    private List<Task> loadQuestTasks(String questId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT type, item_id, item_count, consume, advancement_id, dimension, x, y, z, radius
                FROM quest_tasks
                WHERE quest_id = ?
                ORDER BY task_index
                """)) {
            statement.setString(1, questId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Task> tasks = new ArrayList<>();
                while (resultSet.next()) {
                    String type = resultSet.getString("type");
                    switch (type) {
                        case "item" -> {
                            String itemId = resultSet.getString("item_id");
                            int count = resultSet.getInt("item_count");
                            if (resultSet.wasNull() || count < 1) {
                                count = 1;
                            }
                            boolean consume = resultSet.getInt("consume") != 0;
                            if (itemId != null) {
                                tasks.add(new ItemTask(new ItemRef(itemId, count), consume));
                            }
                        }
                        case "advancement" -> {
                            String advancementId = resultSet.getString("advancement_id");
                            if (advancementId != null) {
                                tasks.add(new AdvancementTask(advancementId));
                            }
                        }
                        case "location" -> {
                            String dimension = resultSet.getString("dimension");
                            Double x = getNullableDouble(resultSet, "x");
                            Double y = getNullableDouble(resultSet, "y");
                            Double z = getNullableDouble(resultSet, "z");
                            Double radius = getNullableDouble(resultSet, "radius");
                            if (dimension != null && x != null && y != null && z != null && radius != null) {
                                tasks.add(new LocationTask(dimension, x, y, z, radius));
                            }
                        }
                        default -> {
                            // Ignore unsupported task types
                        }
                    }
                }
                return tasks;
            }
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
                statement.setString(3, reward.type().name().toLowerCase(Locale.ROOT));

                setStringOrNull(statement, 4, null);
                setIntegerOrNull(statement, 5, null);
                setStringOrNull(statement, 6, null);
                setIntegerOrNull(statement, 7, null);
                setStringOrNull(statement, 8, null);
                setIntegerOrNull(statement, 9, null);

                switch (reward.type()) {
                    case ITEM -> {
                        ItemRef item = reward.item().orElseThrow();
                        statement.setString(4, item.itemId());
                        statement.setInt(5, item.count());
                    }
                    case LOOT_TABLE -> setStringOrNull(statement, 6, reward.lootTableId().orElse(null));
                    case EXPERIENCE -> statement.setInt(7, reward.experience().orElse(0));
                    case COMMAND -> {
                        RewardCommand command = reward.command().orElseThrow();
                        statement.setString(8, command.command());
                        statement.setInt(9, command.runAsServer() ? 1 : 0);
                    }
                }

                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private List<Reward> loadQuestRewards(String questId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT type, item_id, item_count, loot_table_id, experience, command, run_as_server
                FROM quest_rewards
                WHERE quest_id = ?
                ORDER BY reward_index
                """)) {
            statement.setString(1, questId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Reward> rewards = new ArrayList<>();
                while (resultSet.next()) {
                    String type = resultSet.getString("type");
                    if (type == null) {
                        continue;
                    }
                    RewardType rewardType;
                    try {
                        rewardType = RewardType.valueOf(type.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        continue;
                    }
                    switch (rewardType) {
                        case ITEM -> {
                            String itemId = resultSet.getString("item_id");
                            int count = resultSet.getInt("item_count");
                            if (resultSet.wasNull() || count < 1) {
                                count = 1;
                            }
                            if (itemId != null) {
                                rewards.add(Reward.item(new ItemRef(itemId, count)));
                            }
                        }
                        case LOOT_TABLE -> {
                            String lootTableId = resultSet.getString("loot_table_id");
                            if (lootTableId != null) {
                                rewards.add(Reward.lootTable(lootTableId));
                            }
                        }
                        case EXPERIENCE -> {
                            int amount = resultSet.getInt("experience");
                            if (!resultSet.wasNull()) {
                                rewards.add(Reward.experience(amount));
                            }
                        }
                        case COMMAND -> {
                            String command = resultSet.getString("command");
                            boolean runAsServer = resultSet.getInt("run_as_server") != 0;
                            if (command != null && !command.isBlank()) {
                                rewards.add(Reward.command(new RewardCommand(command, runAsServer)));
                            }
                        }
                    }
                }
                return rewards;
            }
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

    private List<Dependency> loadQuestDependencies(String questId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT dependency_quest_id, required
                FROM quest_dependencies
                WHERE quest_id = ?
                ORDER BY dependency_quest_id
                """)) {
            statement.setString(1, questId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Dependency> dependencies = new ArrayList<>();
                while (resultSet.next()) {
                    String dependencyQuestId = resultSet.getString("dependency_quest_id");
                    boolean required = resultSet.getInt("required") != 0;
                    dependencies.add(new Dependency(dependencyQuestId, required));
                }
                return dependencies;
            }
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

    private static Double getNullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        if (resultSet.wasNull()) {
            return null;
        }
        return value;
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

    public record QuestPosition(String questId, double x, double y) {
    }

    public enum SortMode {
        NAME,
        MOD,
        VANILLA_FIRST
    }
}
