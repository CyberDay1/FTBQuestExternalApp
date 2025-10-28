package dev.ftbq.editor.io.snbt;

import dev.ftbq.editor.domain.AdvancementTask;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.ItemTask;
import dev.ftbq.editor.domain.LocationTask;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.domain.Reward;
import dev.ftbq.editor.domain.Task;
import dev.ftbq.editor.importer.snbt.model.ImportOptions;
import dev.ftbq.editor.importer.snbt.service.SnbtQuestImporter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Maps quest domain objects to their SNBT representation.
 */
public class SnbtQuestMapper {

    private static final String INDENT = "  ";

    public String toSnbt(QuestFile file) {
        var builder = new StringBuilder();
        appendLine(builder, 0, "{");
        appendLine(builder, 1, "id:\"" + escape(file.id()) + "\",");
        appendLine(builder, 1, "title:\"" + escape(file.title()) + "\",");
        appendLine(builder, 1, "chapters:[");
        var chapters = file.chapters();
        for (int i = 0; i < chapters.size(); i++) {
            appendChapter(builder, chapters.get(i), i == chapters.size() - 1);
        }
        appendLine(builder, 1, "]");
        appendLine(builder, 0, "}");
        return builder.toString();
    }

    private void appendChapter(StringBuilder builder, Chapter chapter, boolean last) {
        appendLine(builder, 2, "{");
        appendLine(builder, 3, "id:\"" + escape(chapter.id()) + "\",");
        appendLine(builder, 3, "title:\"" + escape(chapter.title()) + "\",");
        appendLine(builder, 3, "icon:\"" + escape(chapter.icon().icon()) + "\",");
        appendLine(builder, 3, "background:\"" + escape(chapter.background().texture()) + "\",");
        appendLine(builder, 3, "visibility:\"" + chapter.visibility().name().toLowerCase(Locale.ROOT) + "\",");
        appendLine(builder, 3, "quests:[");
        var quests = chapter.quests();
        for (int i = 0; i < quests.size(); i++) {
            appendQuest(builder, quests.get(i), i == quests.size() - 1);
        }
        appendLine(builder, 3, "]");
        appendLine(builder, 2, "}" + (last ? "" : ","));
    }

    private void appendQuest(StringBuilder builder, Quest quest, boolean last) {
        appendLine(builder, 4, "{");
        appendLine(builder, 5, "id:\"" + escape(quest.id()) + "\",");
        appendLine(builder, 5, "title:\"" + escape(quest.title()) + "\",");
        appendLine(builder, 5, "description:\"" + escape(quest.description()) + "\",");
        appendLine(builder, 5, "icon:\"" + escape(quest.icon().icon()) + "\",");
        appendLine(builder, 5, "visibility:\"" + quest.visibility().name().toLowerCase(Locale.ROOT) + "\",");
        appendLine(builder, 5, "tasks:[" + formatList(tasksToSnbt(quest.tasks())) + "],");
        appendLine(builder, 5, "rewards:[" + formatList(rewardsToSnbt(quest.rewards())) + "],");
        appendLine(builder, 5, "dependencies:[" + formatList(depsToSnbt(quest.dependencies())) + "]");
        appendLine(builder, 4, "}" + (last ? "" : ","));
    }

    private String formatList(List<String> entries) {
        if (entries.isEmpty()) {
            return "";
        }
        var joiner = new StringJoiner(", ");
        entries.forEach(joiner::add);
        return joiner.toString();
    }

    private List<String> tasksToSnbt(List<Task> tasks) {
        var entries = new ArrayList<String>(tasks.size());
        for (Task task : tasks) {
            if (task instanceof ItemTask itemTask) {
                entries.add("{type:\"item\", item:" + itemRefToSnbt(itemTask.item()) + ", consume:" + booleanToByte(itemTask.consume()) + "}");
            } else if (task instanceof AdvancementTask advancementTask) {
                entries.add("{type:\"advancement\", advancement:\"" + escape(advancementTask.advancementId()) + "\"}");
            } else if (task instanceof LocationTask locationTask) {
                entries.add("{type:\"location\", dimension:\"" + escape(locationTask.dimension()) + "\", x:" + formatDouble(locationTask.x())
                        + ", y:" + formatDouble(locationTask.y()) + ", z:" + formatDouble(locationTask.z()) + ", radius:" + formatDouble(locationTask.radius()) + "}");
            } else {
                throw new IllegalArgumentException("Unsupported task type: " + task);
            }
        }
        return entries;
    }

    private List<String> rewardsToSnbt(List<Reward> rewards) {
        var entries = new ArrayList<String>(rewards.size());
        for (Reward reward : rewards) {
            entries.add(rewardToSnbt(reward));
        }
        return entries;
    }

    private String rewardToSnbt(Reward reward) {
        return switch (reward.type()) {
            case ITEM -> "{type:\"item\", item:" + reward.item().map(this::itemRefToSnbt).orElseThrow() + "}";
            case LOOT_TABLE -> "{type:\"loot_table\", table:\"" + escape(reward.lootTableId().orElseThrow()) + "\"}";
            case XP_LEVELS -> "{type:\"xp_levels\", amount:" + reward.experienceLevels().orElseThrow() + "}";
            case XP_AMOUNT -> "{type:\"xp_amount\", amount:" + reward.experienceAmount().orElseThrow() + "}";
            case COMMAND -> {
                var command = reward.command().orElseThrow();
                yield "{type:\"command\", command:\"" + escape(command.command()) + "\", run_as_server:" + booleanToByte(command.runAsServer()) + "}";
            }
        };
    }

    private List<String> depsToSnbt(List<Dependency> dependencies) {
        var entries = new ArrayList<String>(dependencies.size());
        for (Dependency dependency : dependencies) {
            entries.add("{quest:\"" + escape(dependency.questId()) + "\", required:" + booleanToByte(dependency.required()) + "}");
        }
        return entries;
    }

    public QuestFile fromSnbt(String snbtText) {
        var importer = new SnbtQuestImporter();
        var pack = importer.parse(snbtText);
        var baseFile = QuestFile.builder()
                .id(pack.id())
                .title(pack.title())
                .build();
        var options = ImportOptions.builder()
                .copyAssets(false)
                .build();
        return importer.merge(baseFile, pack, options).questFile();
    }

    private String itemRefToSnbt(ItemRef ref) {
        return "{id:\"" + escape(ref.itemId()) + "\", count:" + ref.count() + "}";
    }

    private String formatDouble(double value) {
        var plain = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
        if (!plain.contains(".")) {
            plain = plain + ".0";
        }
        return plain + "d";
    }

    private String booleanToByte(boolean value) {
        return value ? "1b" : "0b";
    }

    private void appendLine(StringBuilder builder, int indent, String line) {
        builder.append(INDENT.repeat(indent)).append(line).append('\n');
    }

    private static String escape(String s) {
        return s.replace("\"", "\\\"");
    }
}
