package dev.ftbq.editor.store;

import dev.ftbq.editor.domain.AdvancementTask;
import dev.ftbq.editor.domain.CommandReward;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.ItemReward;
import dev.ftbq.editor.domain.ItemTask;
import dev.ftbq.editor.domain.LocationTask;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.domain.XpReward;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestPersistenceTest {

    @Test
    void saveQuestPersistsQuestGraph() throws Exception {
        try (Connection connection = Jdbc.openInMemory()) {
            StoreDao dao = new StoreDao(connection);
            Quest quest = Quest.builder()
                    .id("quest_1")
                    .title("Apples for Everyone")
                    .description("Gather items and explore.")
                    .icon(new IconRef("minecraft:apple", Optional.of("icons/apple.png")))
                    .visibility(Visibility.HIDDEN)
                    .tasks(List.of(
                            new ItemTask(new ItemRef("minecraft:apple", 3), true),
                            new AdvancementTask("minecraft:story/root"),
                            new LocationTask("minecraft:overworld", 10.5, 64, -12.75, 5.0)
                    ))
                    .rewards(List.of(
                            new ItemReward(new ItemRef("minecraft:diamond", 2)),
                            new XpReward(100),
                            new CommandReward("/say Quest complete!", true),
                            new CommandReward("/give @p minecraft:apple", false),
                            new dev.ftbq.editor.domain.CustomReward("custom", Map.of("rarity", "legendary"))
                    ))
                    .dependencies(List.of(
                            new Dependency("quest_0", true),
                            new Dependency("quest_side", false)
                    ))
                    .build();

            dao.saveQuest(quest);

            try (PreparedStatement questStatement = connection.prepareStatement(
                    "SELECT title, description, icon, icon_relative_path, visibility FROM quests WHERE id = ?")) {
                questStatement.setString(1, quest.id());
                try (ResultSet resultSet = questStatement.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertEquals("Apples for Everyone", resultSet.getString("title"));
                    assertEquals("Gather items and explore.", resultSet.getString("description"));
                    assertEquals("minecraft:apple", resultSet.getString("icon"));
                    assertEquals("icons/apple.png", resultSet.getString("icon_relative_path"));
                    assertEquals("HIDDEN", resultSet.getString("visibility"));
                    assertFalse(resultSet.next());
                }
            }

            try (PreparedStatement taskStatement = connection.prepareStatement(
                    "SELECT task_index, type, item_id, item_count, consume, advancement_id, dimension, x, y, z, radius " +
                            "FROM quest_tasks WHERE quest_id = ? ORDER BY task_index")) {
                taskStatement.setString(1, quest.id());
                try (ResultSet resultSet = taskStatement.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertEquals(0, resultSet.getInt("task_index"));
                    assertEquals("item", resultSet.getString("type"));
                    assertEquals("minecraft:apple", resultSet.getString("item_id"));
                    assertEquals(3, resultSet.getInt("item_count"));
                    assertEquals(1, resultSet.getInt("consume"));
                    assertNull(resultSet.getString("advancement_id"));
                    assertNull(resultSet.getString("dimension"));
                    assertNull(resultSet.getObject("x"));
                    assertNull(resultSet.getObject("y"));
                    assertNull(resultSet.getObject("z"));
                    assertNull(resultSet.getObject("radius"));

                    assertTrue(resultSet.next());
                    assertEquals(1, resultSet.getInt("task_index"));
                    assertEquals("advancement", resultSet.getString("type"));
                    assertNull(resultSet.getString("item_id"));
                    assertNull(resultSet.getObject("item_count"));
                    assertNull(resultSet.getObject("consume"));
                    assertEquals("minecraft:story/root", resultSet.getString("advancement_id"));
                    assertNull(resultSet.getString("dimension"));

                    assertTrue(resultSet.next());
                    assertEquals(2, resultSet.getInt("task_index"));
                    assertEquals("location", resultSet.getString("type"));
                    assertEquals("minecraft:overworld", resultSet.getString("dimension"));
                    assertEquals(10.5, resultSet.getDouble("x"), 1e-6);
                    assertEquals(64.0, resultSet.getDouble("y"), 1e-6);
                    assertEquals(-12.75, resultSet.getDouble("z"), 1e-6);
                    assertEquals(5.0, resultSet.getDouble("radius"), 1e-6);
                    assertNull(resultSet.getString("item_id"));
                    assertNull(resultSet.getObject("item_count"));
                    assertNull(resultSet.getObject("consume"));
                    assertNull(resultSet.getString("advancement_id"));

                    assertFalse(resultSet.next());
                }
            }

            try (PreparedStatement rewardStatement = connection.prepareStatement(
                    "SELECT reward_index, type, item_id, item_count, amount, command, as_player, metadata " +
                            "FROM quest_rewards WHERE quest_id = ? ORDER BY reward_index")) {
                rewardStatement.setString(1, quest.id());
                try (ResultSet resultSet = rewardStatement.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertEquals(0, resultSet.getInt("reward_index"));
                    assertEquals("item", resultSet.getString("type"));
                    assertEquals("minecraft:diamond", resultSet.getString("item_id"));
                    assertEquals(2, resultSet.getInt("item_count"));
                    assertNull(resultSet.getObject("amount"));
                    assertNull(resultSet.getString("command"));

                    assertTrue(resultSet.next());
                    assertEquals(1, resultSet.getInt("reward_index"));
                    assertEquals("xp", resultSet.getString("type"));
                    assertEquals(100, resultSet.getInt("amount"));
                    assertNull(resultSet.getString("command"));

                    assertTrue(resultSet.next());
                    assertEquals(2, resultSet.getInt("reward_index"));
                    assertEquals("command", resultSet.getString("type"));
                    assertEquals("/say Quest complete!", resultSet.getString("command"));
                    assertEquals(1, resultSet.getInt("as_player"));

                    assertTrue(resultSet.next());
                    assertEquals(3, resultSet.getInt("reward_index"));
                    assertEquals("command", resultSet.getString("type"));
                    assertEquals("/give @p minecraft:apple", resultSet.getString("command"));
                    assertEquals(0, resultSet.getInt("as_player"));

                    assertTrue(resultSet.next());
                    assertEquals(4, resultSet.getInt("reward_index"));
                    assertEquals("custom", resultSet.getString("type"));
                    String metadata = resultSet.getString("metadata");
                    assertNotNull(metadata);
                    assertTrue(metadata.contains("\"rarity\":\"legendary\""));
                    assertNull(resultSet.getString("command"));

                    assertFalse(resultSet.next());
                }
            }

            try (PreparedStatement dependencyStatement = connection.prepareStatement(
                    "SELECT dependency_quest_id, required FROM quest_dependencies WHERE quest_id = ? ORDER BY dependency_quest_id")) {
                dependencyStatement.setString(1, quest.id());
                try (ResultSet resultSet = dependencyStatement.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertEquals("quest_0", resultSet.getString("dependency_quest_id"));
                    assertEquals(1, resultSet.getInt("required"));

                    assertTrue(resultSet.next());
                    assertEquals("quest_side", resultSet.getString("dependency_quest_id"));
                    assertEquals(0, resultSet.getInt("required"));

                    assertFalse(resultSet.next());
                }
            }
        }
    }

    @Test
    void saveQuestReplacesExistingRecords() throws Exception {
        try (Connection connection = Jdbc.openInMemory()) {
            StoreDao dao = new StoreDao(connection);
            Quest initial = Quest.builder()
                    .id("quest_update")
                    .title("Initial Title")
                    .description("Initial description")
                    .icon(new IconRef("minecraft:book"))
                    .tasks(List.of(new ItemTask(new ItemRef("minecraft:apple", 1), false)))
                    .rewards(List.of(new XpReward(10)))
                    .dependencies(List.of(new Dependency("quest_a", true)))
                    .build();

            dao.saveQuest(initial);

            Quest updated = Quest.builder()
                    .id("quest_update")
                    .title("Updated Title")
                    .description("Updated description")
                    .icon(new IconRef("minecraft:diamond"))
                    .visibility(Visibility.HIDDEN)
                    .tasks(List.of(new AdvancementTask("minecraft:adventure/root")))
                    .rewards(List.of(new CommandReward("/title", false)))
                    .dependencies(List.of(new Dependency("quest_b", false)))
                    .build();

            dao.saveQuest(updated);

            try (PreparedStatement questStatement = connection.prepareStatement(
                    "SELECT title, description, icon, visibility FROM quests WHERE id = ?")) {
                questStatement.setString(1, updated.id());
                try (ResultSet resultSet = questStatement.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertEquals("Updated Title", resultSet.getString("title"));
                    assertEquals("Updated description", resultSet.getString("description"));
                    assertEquals("minecraft:diamond", resultSet.getString("icon"));
                    assertEquals("HIDDEN", resultSet.getString("visibility"));
                }
            }

            try (PreparedStatement taskStatement = connection.prepareStatement(
                    "SELECT COUNT(*) AS cnt FROM quest_tasks WHERE quest_id = ?")) {
                taskStatement.setString(1, updated.id());
                try (ResultSet resultSet = taskStatement.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertEquals(1, resultSet.getInt("cnt"));
                }
            }

            try (PreparedStatement rewardStatement = connection.prepareStatement(
                    "SELECT type, command, as_player FROM quest_rewards WHERE quest_id = ?")) {
                rewardStatement.setString(1, updated.id());
                try (ResultSet resultSet = rewardStatement.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertEquals("command", resultSet.getString("type"));
                    assertEquals("/title", resultSet.getString("command"));
                    assertEquals(0, resultSet.getInt("as_player"));
                    assertFalse(resultSet.next());
                }
            }

            try (PreparedStatement dependencyStatement = connection.prepareStatement(
                    "SELECT dependency_quest_id, required FROM quest_dependencies WHERE quest_id = ?")) {
                dependencyStatement.setString(1, updated.id());
                try (ResultSet resultSet = dependencyStatement.executeQuery()) {
                    assertTrue(resultSet.next());
                    assertEquals("quest_b", resultSet.getString("dependency_quest_id"));
                    assertEquals(0, resultSet.getInt("required"));
                    assertFalse(resultSet.next());
                }
            }
        }
    }
}
