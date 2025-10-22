package dev.ftbq.editor.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.ftbq.editor.domain.AdvancementTask;
import dev.ftbq.editor.domain.BackgroundAlignment;
import dev.ftbq.editor.domain.BackgroundRef;
import dev.ftbq.editor.domain.BackgroundRepeat;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.ChapterGroup;
import dev.ftbq.editor.domain.CommandReward;
import dev.ftbq.editor.domain.CustomReward;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.ItemReward;
import dev.ftbq.editor.domain.ItemTask;
import dev.ftbq.editor.domain.LocationTask;
import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.domain.Reward;
import dev.ftbq.editor.domain.Task;
import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.domain.XpReward;
import dev.ftbq.editor.io.model.ItemRefData;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class QuestFileJson {
    private static final String FILE_NAME = "quest-file.json";

    private QuestFileJson() {
    }

    public static QuestFile load(Path root) throws IOException {
        Objects.requireNonNull(root, "root");
        Path jsonPath = root.resolve(FILE_NAME);
        QuestFileData data = JsonConfig.OBJECT_MAPPER.readValue(jsonPath.toFile(), QuestFileData.class);
        return data.toDomain();
    }

    public static void save(QuestFile questFile, Path root) throws IOException {
        Objects.requireNonNull(questFile, "questFile");
        Objects.requireNonNull(root, "root");
        Path jsonPath = root.resolve(FILE_NAME);
        Files.createDirectories(jsonPath.getParent());
        QuestFileData data = QuestFileData.fromDomain(questFile);
        JsonConfig.OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), data);
    }

    private record QuestFileData(@JsonProperty("id") String id,
                                 @JsonProperty("title") String title,
                                 @JsonProperty("chapter_groups") List<ChapterGroupData> chapterGroups,
                                 @JsonProperty("chapters") List<ChapterData> chapters,
                                 @JsonProperty("loot_tables") List<LootTableJson.LootTableData> lootTables) {

        private static QuestFileData fromDomain(QuestFile questFile) {
            return new QuestFileData(
                    questFile.id(),
                    questFile.title(),
                    questFile.chapterGroups().stream().map(ChapterGroupData::fromDomain).collect(Collectors.toList()),
                    questFile.chapters().stream().map(ChapterData::fromDomain).collect(Collectors.toList()),
                    questFile.lootTables().stream().map(LootTableJson::toData).collect(Collectors.toList())
            );
        }

        private QuestFile toDomain() {
            return new QuestFile(
                    id,
                    title,
                    chapterGroups.stream().map(ChapterGroupData::toDomain).collect(Collectors.toList()),
                    chapters.stream().map(ChapterData::toDomain).collect(Collectors.toList()),
                    lootTables == null ? List.of() : lootTables.stream().map(LootTableJson::fromData).collect(Collectors.toList())
            );
        }
    }

    private record ChapterGroupData(@JsonProperty("id") String id,
                                    @JsonProperty("title") String title,
                                    @JsonProperty("icon") IconRefData icon,
                                    @JsonProperty("chapter_ids") List<String> chapterIds,
                                    @JsonProperty("visibility") Visibility visibility) {

        private static ChapterGroupData fromDomain(ChapterGroup group) {
            return new ChapterGroupData(
                    group.id(),
                    group.title(),
                    IconRefData.fromDomain(group.icon()),
                    new ArrayList<>(group.chapterIds()),
                    group.visibility()
            );
        }

        private ChapterGroup toDomain() {
            return new ChapterGroup(id, title, icon.toDomain(), new ArrayList<>(chapterIds), visibility);
        }
    }

    private record ChapterData(@JsonProperty("id") String id,
                               @JsonProperty("title") String title,
                               @JsonProperty("icon") IconRefData icon,
                               @JsonProperty("background") BackgroundRefData background,
                               @JsonProperty("quests") List<QuestData> quests,
                               @JsonProperty("visibility") Visibility visibility) {

        private static ChapterData fromDomain(Chapter chapter) {
            return new ChapterData(
                    chapter.id(),
                    chapter.title(),
                    IconRefData.fromDomain(chapter.icon()),
                    BackgroundRefData.fromDomain(chapter.background()),
                    chapter.quests().stream().map(QuestData::fromDomain).collect(Collectors.toList()),
                    chapter.visibility()
            );
        }

        private Chapter toDomain() {
            return new Chapter(
                    id,
                    title,
                    icon.toDomain(),
                    background.toDomain(),
                    quests.stream().map(QuestData::toDomain).collect(Collectors.toList()),
                    visibility
            );
        }
    }

    private record QuestData(@JsonProperty("id") String id,
                             @JsonProperty("title") String title,
                             @JsonProperty("description") String description,
                             @JsonProperty("icon") IconRefData icon,
                             @JsonProperty("tasks") List<TaskData> tasks,
                             @JsonProperty("rewards") List<RewardData> rewards,
                             @JsonProperty("dependencies") List<DependencyData> dependencies,
                             @JsonProperty("visibility") Visibility visibility) {

        private static QuestData fromDomain(Quest quest) {
            return new QuestData(
                    quest.id(),
                    quest.title(),
                    quest.description(),
                    IconRefData.fromDomain(quest.icon()),
                    quest.tasks().stream().map(TaskData::fromDomain).collect(Collectors.toList()),
                    quest.rewards().stream().map(RewardData::fromDomain).collect(Collectors.toList()),
                    quest.dependencies().stream().map(DependencyData::fromDomain).collect(Collectors.toList()),
                    quest.visibility()
            );
        }

        private Quest toDomain() {
            return new Quest(
                    id,
                    title,
                    description,
                    icon.toDomain(),
                    tasks.stream().map(TaskData::toDomain).collect(Collectors.toList()),
                    rewards.stream().map(RewardData::toDomain).collect(Collectors.toList()),
                    dependencies.stream().map(DependencyData::toDomain).collect(Collectors.toList()),
                    visibility
            );
        }
    }

    private record TaskData(@JsonProperty("type") String type,
                            @JsonProperty("item") ItemRefData item,
                            @JsonProperty("consume") Boolean consume,
                            @JsonProperty("advancement_id") String advancementId,
                            @JsonProperty("location") LocationData location) {

        private static TaskData fromDomain(Task task) {
            if (task instanceof ItemTask itemTask) {
                return new TaskData("item", ItemRefData.fromDomain(itemTask.item()), itemTask.consume(), null, null);
            } else if (task instanceof AdvancementTask advancementTask) {
                return new TaskData("advancement", null, null, advancementTask.advancementId(), null);
            } else if (task instanceof LocationTask locationTask) {
                return new TaskData("location", null, null, null, LocationData.fromDomain(locationTask));
            }
            throw new IllegalArgumentException("Unsupported task type: " + task.type());
        }

        private Task toDomain() {
            return switch (type) {
                case "item" -> new ItemTask(item.toDomain(), Boolean.TRUE.equals(consume));
                case "advancement" -> new AdvancementTask(advancementId);
                case "location" -> location.toDomain();
                default -> throw new IllegalArgumentException("Unknown task type: " + type);
            };
        }
    }

    private record LocationData(@JsonProperty("dimension") String dimension,
                                @JsonProperty("x") double x,
                                @JsonProperty("y") double y,
                                @JsonProperty("z") double z,
                                @JsonProperty("radius") double radius) {

        private static LocationData fromDomain(LocationTask task) {
            return new LocationData(task.dimension(), task.x(), task.y(), task.z(), task.radius());
        }

        private LocationTask toDomain() {
            return new LocationTask(dimension, x, y, z, radius);
        }
    }

    private record RewardData(@JsonProperty("type") String type,
                              @JsonProperty("item") ItemRefData item,
                              @JsonProperty("amount") Integer amount,
                              @JsonProperty("command") String command,
                              @JsonProperty("as_player") Boolean asPlayer,
                              @JsonProperty("metadata") Map<String, Object> metadata) {

        private static RewardData fromDomain(Reward reward) {
            if (reward instanceof ItemReward itemReward) {
                return new RewardData("item", ItemRefData.fromDomain(itemReward.item()), null, null, null, Map.of());
            } else if (reward instanceof XpReward xpReward) {
                return new RewardData("xp", null, xpReward.amount(), null, null, Map.of());
            } else if (reward instanceof CommandReward commandReward) {
                return new RewardData("command", null, null, commandReward.command(), commandReward.asPlayer(), Map.of());
            } else if (reward instanceof CustomReward customReward) {
                return new RewardData(customReward.type(), null, null, null, null, customReward.metadata());
            }
            throw new IllegalArgumentException("Unsupported reward type: " + reward.type());
        }

        private Reward toDomain() {
            return switch (type) {
                case "item" -> new ItemReward(item.toDomain());
                case "xp" -> new XpReward(amount);
                case "command" -> new CommandReward(command, Boolean.TRUE.equals(asPlayer));
                default -> new CustomReward(type, metadata == null ? Map.of() : metadata);
            };
        }
    }

    private record DependencyData(@JsonProperty("quest_id") String questId,
                                  @JsonProperty("required") boolean required) {

        private static DependencyData fromDomain(Dependency dependency) {
            return new DependencyData(dependency.questId(), dependency.required());
        }

        private Dependency toDomain() {
            return new Dependency(questId, required);
        }
    }

    private record IconRefData(@JsonProperty("icon") String icon) {

        private static IconRefData fromDomain(IconRef icon) {
            return new IconRefData(icon.icon());
        }

        private IconRef toDomain() {
            return new IconRef(icon);
        }
    }

    private record BackgroundRefData(@JsonProperty("texture") String texture,
                                     @JsonProperty("alignment") @JsonInclude(JsonInclude.Include.NON_EMPTY) Optional<BackgroundAlignment> alignment,
                                     @JsonProperty("repeat") @JsonInclude(JsonInclude.Include.NON_EMPTY) Optional<BackgroundRepeat> repeat) {

        private static BackgroundRefData fromDomain(BackgroundRef background) {
            return new BackgroundRefData(background.texture(), background.alignment(), background.repeat());
        }

        private BackgroundRef toDomain() {
            return new BackgroundRef(texture, alignment == null ? Optional.empty() : alignment, repeat == null ? Optional.empty() : repeat);
        }
    }
}
