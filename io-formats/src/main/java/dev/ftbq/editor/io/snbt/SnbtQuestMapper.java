package dev.ftbq.editor.io.snbt;

import dev.ftbq.editor.domain.AdvancementTask;
import dev.ftbq.editor.domain.BiomeTask;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.ChapterImage;
import dev.ftbq.editor.domain.CheckmarkTask;
import dev.ftbq.editor.domain.CustomTask;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.ChapterGroup;
import dev.ftbq.editor.domain.DimensionTask;
import dev.ftbq.editor.domain.FluidTask;
import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.ItemReward;
import dev.ftbq.editor.domain.ItemTask;
import dev.ftbq.editor.domain.KillTask;
import dev.ftbq.editor.domain.LocationTask;
import dev.ftbq.editor.domain.LootEntry;
import dev.ftbq.editor.domain.LootPool;
import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.domain.ObservationTask;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.domain.QuestLink;
import dev.ftbq.editor.domain.QuestShape;
import dev.ftbq.editor.domain.RewardCommand;
import dev.ftbq.editor.domain.StageTask;
import dev.ftbq.editor.domain.StatTask;
import dev.ftbq.editor.domain.StructureTask;
import dev.ftbq.editor.domain.Task;
import dev.ftbq.editor.domain.XpTask;
import dev.ftbq.editor.importer.snbt.model.ImportOptions;
import dev.ftbq.editor.importer.snbt.service.SnbtQuestImporter;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Maps quest domain objects to their SNBT representation.
 */
public class SnbtQuestMapper {

    private static final String INDENT = "  ";

    public Fragments fragmentsFor(QuestFile file) {
        Objects.requireNonNull(file, "file");
        SnbtIdRegistry ids = new SnbtIdRegistry(file);
        String data = toSnbt(file);
        String chapterGroups = buildChapterGroups(file, ids);
        Map<Chapter, String> chapterSnippets = new LinkedHashMap<>();
        for (Chapter chapter : file.chapters()) {
            chapterSnippets.put(chapter, buildChapterSnippet(chapter, ids));
        }
        Map<LootTable, String> rewardSnippets = new LinkedHashMap<>();
        for (LootTable table : file.lootTables()) {
            rewardSnippets.put(table, buildLootTableSnippet(table));
        }
        return new Fragments(data, chapterGroups, chapterSnippets, rewardSnippets);
    }

    public String toSnbt(QuestFile file) {
        var builder = new StringBuilder();
        var ids = new SnbtIdRegistry(file);
        appendLine(builder, 0, "{");
        appendLine(builder, 1, "id:\"" + escape(file.id()) + "\",");
        appendLine(builder, 1, "title:\"" + escape(file.title()) + "\",");
        appendLine(builder, 1, "chapters:[");
        var chapters = file.chapters();
        for (int i = 0; i < chapters.size(); i++) {
            appendChapter(builder, chapters.get(i), i == chapters.size() - 1, ids);
        }
        appendLine(builder, 1, "],");
        appendLootTables(builder, file.lootTables());
        appendLine(builder, 0, "}");
        return builder.toString();
    }

    private String buildChapterSnippet(Chapter chapter, SnbtIdRegistry ids) {
        var builder = new StringBuilder();
        appendChapter(builder, chapter, true, ids);
        return builder.toString();
    }

    private void appendChapter(StringBuilder builder, Chapter chapter, boolean last, SnbtIdRegistry ids) {
        appendLine(builder, 2, "{");
        appendLine(builder, 3, "id:\"" + ids.hexIdForChapter(chapter) + "\",");
        appendLine(builder, 3, "title:\"" + escape(chapter.title()) + "\",");
        appendLine(builder, 3, "icon:\"" + escape(chapter.icon().icon()) + "\",");
        appendLine(builder, 3, "background:\"" + escape(chapter.background().texture()) + "\",");
        appendLine(builder, 3, "visibility:\"" + chapter.visibility().name().toLowerCase(Locale.ROOT) + "\",");
        appendImages(builder, chapter.images());
        appendQuestLinks(builder, chapter.questLinks(), ids);
        appendLine(builder, 3, "quests:[");
        var quests = chapter.quests();
        for (int i = 0; i < quests.size(); i++) {
            appendQuest(builder, quests.get(i), i == quests.size() - 1, ids);
        }
        appendLine(builder, 3, "]");
        appendLine(builder, 2, "}" + (last ? "" : ","));
    }

    private void appendQuest(StringBuilder builder, Quest quest, boolean last, SnbtIdRegistry ids) {
        appendLine(builder, 4, "{");
        appendLine(builder, 5, "id:\"" + ids.hexIdForQuest(quest) + "\",");
        appendLine(builder, 5, "title:\"" + escape(quest.title()) + "\",");
        appendLine(builder, 5, "description:\"" + escape(quest.description()) + "\",");
        appendLine(builder, 5, "icon:\"" + escape(quest.icon().icon()) + "\",");
        appendLine(builder, 5, "visibility:\"" + quest.visibility().name().toLowerCase(Locale.ROOT) + "\",");
        if (quest.shape() != null && quest.shape() != QuestShape.DEFAULT) {
            appendLine(builder, 5, "shape:\"" + quest.shape().snbtValue() + "\",");
        }
        if (quest.size() != null) {
            appendLine(builder, 5, "size:" + formatDouble(quest.size()) + ",");
        }
        if (quest.x() != null) {
            appendLine(builder, 5, "x:" + formatDouble(quest.x()) + ",");
        }
        if (quest.y() != null) {
            appendLine(builder, 5, "y:" + formatDouble(quest.y()) + ",");
        }
        appendLine(builder, 5, "tasks:[" + formatList(tasksToSnbt(quest.tasks())) + "],");
        appendLine(builder, 5, "rewards:[" + formatList(rewardsToSnbt(quest)) + "],");
        appendLine(builder, 5, "dependencies:[" + formatList(depsToSnbt(quest.dependencies(), ids)) + "]");
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
                var sb = new StringBuilder("{type:\"item\", item:" + itemRefToSnbt(itemTask.item()));
                if (itemTask.consume()) {
                    sb.append(", consume_items:true");
                }
                sb.append("}");
                entries.add(sb.toString());
            } else if (task instanceof AdvancementTask advancementTask) {
                entries.add("{type:\"advancement\", advancement:\"" + escape(advancementTask.advancementId()) + "\"}");
            } else if (task instanceof LocationTask locationTask) {
                entries.add("{type:\"location\", dimension:\"" + escape(locationTask.dimension()) + "\", x:" + formatDouble(locationTask.x())
                        + ", y:" + formatDouble(locationTask.y()) + ", z:" + formatDouble(locationTask.z()) + ", radius:" + formatDouble(locationTask.radius()) + "}");
            } else if (task instanceof CheckmarkTask) {
                entries.add("{type:\"checkmark\"}");
            } else if (task instanceof KillTask killTask) {
                var sb = new StringBuilder("{type:\"kill\", entity:\"" + escape(killTask.entityType()) + "\", value:" + killTask.count());
                if (killTask.entityTag() != null && !killTask.entityTag().isBlank()) {
                    sb.append(", entity_tag:\"").append(escape(killTask.entityTag())).append("\"");
                }
                if (killTask.customName() != null && !killTask.customName().isBlank()) {
                    sb.append(", custom_name:\"").append(escape(killTask.customName())).append("\"");
                }
                sb.append("}");
                entries.add(sb.toString());
            } else if (task instanceof ObservationTask obsTask) {
                entries.add("{type:\"observation\", observe_type:\"" + obsTask.observeType().name() + "\", to_observe:\"" + escape(obsTask.toObserve()) + "\", timer:" + obsTask.timer() + "L}");
            } else if (task instanceof StageTask stageTask) {
                entries.add("{type:\"gamestage\", stage:\"" + escape(stageTask.stage()) + "\", team_stage:" + booleanToByte(stageTask.teamStage()) + "}");
            } else if (task instanceof DimensionTask dimTask) {
                entries.add("{type:\"dimension\", dimension:\"" + escape(dimTask.dimension()) + "\"}");
            } else if (task instanceof BiomeTask biomeTask) {
                entries.add("{type:\"biome\", biome:\"" + escape(biomeTask.biome()) + "\"}");
            } else if (task instanceof StructureTask structTask) {
                entries.add("{type:\"structure\", structure:\"" + escape(structTask.structure()) + "\"}");
            } else if (task instanceof XpTask xpTask) {
                entries.add("{type:\"xp\", value:" + xpTask.value() + "L, points:" + booleanToByte(xpTask.points()) + "}");
            } else if (task instanceof StatTask statTask) {
                entries.add("{type:\"stat\", stat:\"" + escape(statTask.stat()) + "\", value:" + statTask.value() + "}");
            } else if (task instanceof FluidTask fluidTask) {
                entries.add("{type:\"fluid\", fluid:\"" + escape(fluidTask.fluid()) + "\", amount:" + fluidTask.amount() + "L}");
            } else if (task instanceof CustomTask customTask) {
                entries.add("{type:\"custom\", max_progress:" + customTask.maxProgress() + "L}");
            } else {
                throw new IllegalArgumentException("Unsupported task type: " + task);
            }
        }
        return entries;
    }

    private List<String> rewardsToSnbt(Quest quest) {
        var entries = new ArrayList<String>();
        for (ItemReward itemReward : quest.itemRewards()) {
            entries.add("{type:\"item\", item:" + itemRefToSnbt(itemReward.itemRef()) + "}");
        }
        Integer xpLevels = quest.experienceLevels();
        if (xpLevels != null) {
            entries.add("{type:\"xp_levels\", amount:" + xpLevels + "}");
        }
        Integer xpAmount = quest.experienceAmount();
        if (xpAmount != null) {
            entries.add("{type:\"xp_amount\", amount:" + xpAmount + "}");
        }
        String lootTableId = quest.lootTableId();
        if (lootTableId != null && !lootTableId.isBlank()) {
            entries.add("{type:\"loot_table\", table:\"" + escape(lootTableId) + "\"}");
        }
        RewardCommand command = quest.commandReward();
        if (command != null) {
            entries.add("{type:\"command\", command:\"" + escape(command.command()) + "\", run_as_server:" + booleanToByte(command.runAsServer()) + "}");
        }
        return entries;
    }

    private List<String> depsToSnbt(List<Dependency> dependencies, SnbtIdRegistry ids) {
        var entries = new ArrayList<String>(dependencies.size());
        for (Dependency dependency : dependencies) {
            entries.add("\"" + ids.hexIdForQuestId(dependency.questId()) + "\"");
        }
        return entries;
    }

    private void appendImages(StringBuilder builder, List<ChapterImage> images) {
        if (images == null || images.isEmpty()) {
            appendLine(builder, 3, "images:[],");
            return;
        }
        appendLine(builder, 3, "images:[");
        for (int i = 0; i < images.size(); i++) {
            ChapterImage img = images.get(i);
            var sb = new StringBuilder("{");
            sb.append("image:\"").append(escape(img.image())).append("\"");
            sb.append(", x:").append(formatDouble(img.x())).append("d");
            sb.append(", y:").append(formatDouble(img.y())).append("d");
            sb.append(", width:").append(formatDouble(img.width())).append("d");
            sb.append(", height:").append(formatDouble(img.height())).append("d");
            if (img.rotation() != 0.0) {
                sb.append(", rotation:").append(formatDouble(img.rotation())).append("d");
            }
            if (img.color() != null) {
                sb.append(", color:").append(img.color());
            }
            if (img.alpha() != null) {
                sb.append(", alpha:").append(img.alpha());
            }
            if (!img.hover().isEmpty()) {
                sb.append(", hover:[");
                for (int j = 0; j < img.hover().size(); j++) {
                    sb.append("\"").append(escape(img.hover().get(j))).append("\"");
                    if (j < img.hover().size() - 1) {
                        sb.append(", ");
                    }
                }
                sb.append("]");
            }
            sb.append("}");
            appendLine(builder, 4, sb.toString() + (i < images.size() - 1 ? "," : ""));
        }
        appendLine(builder, 3, "],");
    }

    private void appendQuestLinks(StringBuilder builder, List<QuestLink> questLinks, SnbtIdRegistry ids) {
        if (questLinks == null || questLinks.isEmpty()) {
            appendLine(builder, 3, "quest_links:[],");
            return;
        }
        appendLine(builder, 3, "quest_links:[");
        for (int i = 0; i < questLinks.size(); i++) {
            QuestLink link = questLinks.get(i);
            var sb = new StringBuilder("{");
            sb.append("id:\"").append(escape(link.id())).append("\"");
            sb.append(", linked_quest:\"").append(ids.hexIdForQuestId(link.linkedQuestId())).append("\"");
            sb.append(", x:").append(formatDouble(link.x())).append("d");
            sb.append(", y:").append(formatDouble(link.y())).append("d");
            sb.append("}");
            appendLine(builder, 4, sb.toString() + (i < questLinks.size() - 1 ? "," : ""));
        }
        appendLine(builder, 3, "],");
    }

    private void appendLootTables(StringBuilder builder, List<LootTable> lootTables) {
        if (lootTables == null || lootTables.isEmpty()) {
            appendLine(builder, 1, "loot_tables:[]");
            return;
        }
        appendLine(builder, 1, "loot_tables:[");
        for (int i = 0; i < lootTables.size(); i++) {
            LootTable table = lootTables.get(i);
            appendLine(builder, 2, "{");
            appendLine(builder, 3, "id:\"" + escape(table.id()) + "\",");
            String iconId = table.iconId().orElse("minecraft:book");
            appendLine(builder, 3, "icon:\"" + escape(iconId) + "\",");
            appendLootTableItems(builder, table);
            appendLine(builder, 2, "}" + (i == lootTables.size() - 1 ? "" : ","));
        }
        appendLine(builder, 1, "]");
    }

    private String buildLootTableSnippet(LootTable table) {
        var builder = new StringBuilder();
        appendLootTable(builder, table);
        return builder.toString();
    }

    private void appendLootTableItems(StringBuilder builder, LootTable table) {
        List<LootEntry> entries = new ArrayList<>();
        for (LootPool pool : table.pools()) {
            entries.addAll(pool.entries());
        }
        if (entries.isEmpty()) {
            appendLine(builder, 3, "items:[]");
            return;
        }
        appendLine(builder, 3, "items:[");
        for (int i = 0; i < entries.size(); i++) {
            LootEntry entry = entries.get(i);
            int weight = Math.max(1, (int) Math.round(entry.weight()));
            String itemLine = "{id:\"" + escape(entry.item().itemId()) + "\", count:" + entry.item().count()
                    + ", weight:" + weight + "}";
            appendLine(builder, 4, itemLine + (i == entries.size() - 1 ? "" : ","));
        }
        appendLine(builder, 3, "]");
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

    private String buildChapterGroups(QuestFile file, SnbtIdRegistry ids) {
        var builder = new StringBuilder();
        appendLine(builder, 0, "{");
        appendLine(builder, 1, "chapter_groups:[");
        var groups = file.chapterGroups();
        for (int i = 0; i < groups.size(); i++) {
            appendChapterGroup(builder, groups.get(i), i == groups.size() - 1, ids);
        }
        appendLine(builder, 1, "]");
        appendLine(builder, 0, "}");
        return builder.toString();
    }

    private void appendChapterGroup(StringBuilder builder, ChapterGroup group, boolean last, SnbtIdRegistry ids) {
        appendLine(builder, 2, "{");
        appendLine(builder, 3, "id:\"" + ids.hexIdForChapterGroup(group) + "\",");
        appendLine(builder, 3, "title:\"" + escape(group.title()) + "\",");
        appendLine(builder, 3, "icon:\"" + escape(group.icon().icon()) + "\",");
        appendLine(builder, 3, "visibility:\"" + group.visibility().name().toLowerCase(Locale.ROOT) + "\",");
        appendLine(builder, 3, "chapters:[" + formatChapterList(group, ids) + "]");
        appendLine(builder, 2, "}" + (last ? "" : ","));
    }

    private String formatChapterList(ChapterGroup group, SnbtIdRegistry ids) {
        var joiner = new StringJoiner(", ");
        for (String chapterId : group.chapterIds()) {
            joiner.add("\"" + ids.hexIdForChapterId(chapterId) + "\"");
        }
        return joiner.toString();
    }

    private void appendLootTable(StringBuilder builder, LootTable table) {
        appendLine(builder, 0, "{");
        appendLine(builder, 1, "id:\"" + escape(table.id()) + "\",");
        appendLine(builder, 1, "icon:\"" + escape(table.iconId().orElse("minecraft:book")) + "\",");
        appendLootTableItems(builder, table);
        appendLine(builder, 0, "}");
    }

    public record Fragments(String data,
                            String chapterGroups,
                            Map<Chapter, String> chapters,
                            Map<LootTable, String> rewardTables) {
    }
}
