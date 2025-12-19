package dev.ftbq.editor.viewmodel.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.ftbq.editor.domain.AdvancementReward;
import dev.ftbq.editor.domain.AdvancementTask;
import dev.ftbq.editor.domain.AllTableReward;
import dev.ftbq.editor.domain.ChoiceReward;
import dev.ftbq.editor.domain.CustomReward;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.CommandReward;
import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.ItemReward;
import dev.ftbq.editor.domain.ItemTask;
import dev.ftbq.editor.domain.LocationTask;
import dev.ftbq.editor.domain.LootTableReward;
import dev.ftbq.editor.domain.RandomReward;
import dev.ftbq.editor.domain.Reward;
import dev.ftbq.editor.domain.RewardCommand;
import dev.ftbq.editor.domain.RewardType;
import dev.ftbq.editor.domain.StageReward;
import dev.ftbq.editor.domain.Task;
import dev.ftbq.editor.domain.ToastReward;
import dev.ftbq.editor.domain.XpLevelReward;
import dev.ftbq.editor.domain.XpReward;
import dev.ftbq.editor.services.bus.UndoableCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Locale;

/**
 * Captures undoable changes to quest task, reward, and dependency collections.
 */
public final class QuestListChangeCommand implements UndoableCommand {

    public static final String TYPE = "quest.collection.change";

    public enum CollectionType {
        TASKS,
        REWARDS,
        DEPENDENCIES
    }

    private final String questId;
    private final CollectionType collectionType;
    private final List<?> value;
    private final List<?> previousValue;

    private QuestListChangeCommand(String questId,
                                   CollectionType collectionType,
                                   List<?> value,
                                   List<?> previousValue) {
        this.questId = Objects.requireNonNull(questId, "questId");
        this.collectionType = Objects.requireNonNull(collectionType, "collectionType");
        this.value = List.copyOf(Objects.requireNonNull(value, "value"));
        this.previousValue = List.copyOf(Objects.requireNonNull(previousValue, "previousValue"));
    }

    public static QuestListChangeCommand forTasks(String questId,
                                                  List<Task> value,
                                                  List<Task> previousValue) {
        return new QuestListChangeCommand(questId, CollectionType.TASKS, value, previousValue);
    }

    public static QuestListChangeCommand forRewards(String questId,
                                                    List<Reward> value,
                                                    List<Reward> previousValue) {
        return new QuestListChangeCommand(questId, CollectionType.REWARDS, value, previousValue);
    }

    public static QuestListChangeCommand forDependencies(String questId,
                                                         List<Dependency> value,
                                                         List<Dependency> previousValue) {
        return new QuestListChangeCommand(questId, CollectionType.DEPENDENCIES, value, previousValue);
    }

    public String questId() {
        return questId;
    }

    public CollectionType collectionType() {
        return collectionType;
    }

    @SuppressWarnings("unchecked")
    public List<Task> tasks() {
        ensureType(CollectionType.TASKS);
        return (List<Task>) value;
    }

    @SuppressWarnings("unchecked")
    public List<Task> previousTasks() {
        ensureType(CollectionType.TASKS);
        return (List<Task>) previousValue;
    }

    @SuppressWarnings("unchecked")
    public List<Reward> rewards() {
        ensureType(CollectionType.REWARDS);
        return (List<Reward>) value;
    }

    @SuppressWarnings("unchecked")
    public List<Reward> previousRewards() {
        ensureType(CollectionType.REWARDS);
        return (List<Reward>) previousValue;
    }

    @SuppressWarnings("unchecked")
    public List<Dependency> dependencies() {
        ensureType(CollectionType.DEPENDENCIES);
        return (List<Dependency>) value;
    }

    @SuppressWarnings("unchecked")
    public List<Dependency> previousDependencies() {
        ensureType(CollectionType.DEPENDENCIES);
        return (List<Dependency>) previousValue;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public ObjectNode toPayload() {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("questId", questId);
        node.put("collection", collectionType.name());
        node.set("value", serializeList(collectionType, value));
        node.set("previousValue", serializeList(collectionType, previousValue));
        return node;
    }

    @Override
    public UndoableCommand inverse() {
        return new QuestListChangeCommand(questId, collectionType, previousValue, value);
    }

    public static QuestListChangeCommand fromPayload(ObjectNode payload) {
        String questId = payload.path("questId").asText("");
        String collectionName = payload.path("collection").asText(CollectionType.TASKS.name());
        CollectionType collectionType = CollectionType.valueOf(collectionName);
        JsonNode valueNode = payload.path("value");
        JsonNode previousNode = payload.path("previousValue");

        return switch (collectionType) {
            case TASKS -> QuestListChangeCommand.forTasks(
                    questId,
                    deserializeTasks(valueNode),
                    deserializeTasks(previousNode)
            );
            case REWARDS -> QuestListChangeCommand.forRewards(
                    questId,
                    deserializeRewards(valueNode),
                    deserializeRewards(previousNode)
            );
            case DEPENDENCIES -> QuestListChangeCommand.forDependencies(
                    questId,
                    deserializeDependencies(valueNode),
                    deserializeDependencies(previousNode)
            );
        };
    }

    private static ArrayNode serializeList(CollectionType type, List<?> value) {
        ArrayNode array = JsonNodeFactory.instance.arrayNode();
        for (Object element : value) {
            array.add(switch (type) {
                case TASKS -> serializeTask((Task) element);
                case REWARDS -> serializeReward((Reward) element);
                case DEPENDENCIES -> serializeDependency((Dependency) element);
            });
        }
        return array;
    }

    private static ObjectNode serializeTask(Task task) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", task.type());
        if (task instanceof ItemTask itemTask) {
            node.set("item", serializeItemRef(itemTask.item()));
            node.put("consume", itemTask.consume());
        } else if (task instanceof AdvancementTask advancementTask) {
            node.put("advancementId", advancementTask.advancementId());
        } else if (task instanceof LocationTask locationTask) {
            node.put("dimension", locationTask.dimension());
            node.put("x", locationTask.x());
            node.put("y", locationTask.y());
            node.put("z", locationTask.z());
            node.put("radius", locationTask.radius());
        } else {
            throw new IllegalArgumentException("Unsupported task type: " + task.type());
        }
        return node;
    }

    private static ObjectNode serializeReward(Reward reward) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("type", reward.type().id());
        switch (reward) {
            case ItemReward itemReward -> itemReward.item().ifPresent(item -> node.set("item", serializeItemRef(item)));
            case LootTableReward lootTableReward -> lootTableReward.lootTableId().ifPresent(id -> node.put("lootTableId", id));
            case XpLevelReward xpLevelReward -> node.put("experience", xpLevelReward.experienceLevels().orElse(0));
            case XpReward xpReward -> node.put("experience", xpReward.experienceAmount().orElse(0));
            case CommandReward commandReward -> commandReward.command().ifPresent(command -> {
                node.put("command", command.command());
                node.put("runAsServer", command.runAsServer());
            });
            default -> {
            }
        }
        return node;
    }

    private static ObjectNode serializeDependency(Dependency dependency) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("questId", dependency.questId());
        node.put("required", dependency.required());
        return node;
    }

    private static ObjectNode serializeItemRef(ItemRef ref) {
        ObjectNode node = JsonNodeFactory.instance.objectNode();
        node.put("itemId", ref.itemId());
        node.put("count", ref.count());
        return node;
    }

    private static List<Task> deserializeTasks(JsonNode node) {
        if (node == null || node.isNull()) {
            return Collections.emptyList();
        }
        List<Task> tasks = new ArrayList<>();
        for (JsonNode element : node) {
            String type = element.path("type").asText("");
            tasks.add(switch (type) {
                case "item" -> new ItemTask(deserializeItemRef(element.path("item")), element.path("consume").asBoolean(false));
                case "advancement" -> new AdvancementTask(element.path("advancementId").asText(""));
                case "location" -> new LocationTask(
                        element.path("dimension").asText(""),
                        element.path("x").asDouble(0),
                        element.path("y").asDouble(0),
                        element.path("z").asDouble(0),
                        element.path("radius").asDouble(0)
                );
                default -> throw new IllegalArgumentException("Unsupported task type: " + type);
            });
        }
        return tasks;
    }

    private static List<Reward> deserializeRewards(JsonNode node) {
        if (node == null || node.isNull()) {
            return Collections.emptyList();
        }
        List<Reward> rewards = new ArrayList<>();
        for (JsonNode element : node) {
            String typeText = element.path("type").asText("");
            if (typeText.isBlank()) {
                continue;
            }
            RewardType rewardType;
            try {
                rewardType = RewardType.fromId(typeText);
            } catch (IllegalArgumentException ex) {
                continue;
            }
            Reward reward = switch (rewardType) {
                case ITEM -> Reward.item(deserializeItemRef(element.path("item")));
                case LOOT_TABLE -> {
                    String lootTableId = element.path("lootTableId").asText("");
                    if (lootTableId.isBlank()) {
                        yield null;
                    }
                    yield Reward.lootTable(lootTableId);
                }
                case XP_LEVELS -> Reward.xpLevels(element.path("experience").asInt(0));
                case XP_AMOUNT -> Reward.xpAmount(element.path("experience").asInt(0));
                case COMMAND -> {
                    String commandText = element.path("command").asText("");
                    if (commandText.isBlank()) {
                        yield null;
                    }
                    yield Reward.command(new RewardCommand(commandText, element.path("runAsServer").asBoolean(false)));
                }
                case CHOICE -> new ChoiceReward(element.path("table").asText(""));
                case RANDOM -> new RandomReward(element.path("table").asText(""));
                case ADVANCEMENT -> new AdvancementReward(element.path("advancement").asText(""));
                case STAGE -> new StageReward(element.path("stage").asText(""));
                case TOAST -> new ToastReward(element.path("description").asText(""));
                case CUSTOM -> new CustomReward();
                case ALL_TABLE -> new AllTableReward(element.path("table").asText(""));
            };
            if (reward != null) {
                rewards.add(reward);
            }
        }
        return rewards;
    }

    private static List<Dependency> deserializeDependencies(JsonNode node) {
        if (node == null || node.isNull()) {
            return Collections.emptyList();
        }
        List<Dependency> dependencies = new ArrayList<>();
        for (JsonNode element : node) {
            dependencies.add(new Dependency(
                    element.path("questId").asText(""),
                    element.path("required").asBoolean(false)
            ));
        }
        return dependencies;
    }

    private static ItemRef deserializeItemRef(JsonNode node) {
        if (node == null || node.isNull()) {
            return new ItemRef("minecraft:air", 1);
        }
        String itemId = node.path("itemId").asText("minecraft:air");
        int count = node.path("count").asInt(1);
        return new ItemRef(itemId, Math.max(1, count));
    }

    private void ensureType(CollectionType expected) {
        if (collectionType != expected) {
            throw new IllegalStateException("Command collection type is " + collectionType + " but expected " + expected);
        }
    }
}


