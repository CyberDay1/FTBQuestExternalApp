package dev.ftbq.editor.io.snbt;

import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.ChapterGroup;
import dev.ftbq.editor.domain.HexId;
import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Generates deterministic hex IDs for SNBT export to match FTB Quests expectations.
 * FTB Quests uses 16-character uppercase hexadecimal IDs.
 */
public final class SnbtIdRegistry {

    private final Map<String, String> chapterGroupIds = new LinkedHashMap<>();
    private final Map<String, String> chapterIds = new LinkedHashMap<>();
    private final Map<String, String> questIds = new LinkedHashMap<>();
    private final Map<String, String> lootTableIds = new LinkedHashMap<>();

    public SnbtIdRegistry(QuestFile questFile) {
        Objects.requireNonNull(questFile, "questFile");
        questFile.chapterGroups().forEach(group -> register(chapterGroupIds, group.id(), "group"));
        questFile.chapters().forEach(chapter -> register(chapterIds, chapter.id(), "chapter"));
        questFile.chapters().forEach(chapter ->
                chapter.quests().forEach(quest -> register(questIds, quest.id(), "quest")));
        questFile.lootTables().forEach(table -> register(lootTableIds, table.id(), "table"));
    }

    private void register(Map<String, String> map, String id, String prefix) {
        map.computeIfAbsent(id, key -> toHexId(key, prefix));
    }

    private String lookup(Map<String, String> map, String id, String prefix) {
        Objects.requireNonNull(id, "id");
        return map.computeIfAbsent(id, key -> toHexId(key, prefix));
    }

    private String toHexId(String id, String prefix) {
        if (HexId.isValidHexId(id)) {
            return id;
        }
        return HexId.fromSeed(prefix + ":" + id);
    }

    public String hexIdForChapterGroup(ChapterGroup group) {
        Objects.requireNonNull(group, "group");
        return hexIdForChapterGroupId(group.id());
    }

    public String hexIdForChapterGroupId(String id) {
        return lookup(chapterGroupIds, id, "group");
    }

    public String hexIdForChapter(Chapter chapter) {
        Objects.requireNonNull(chapter, "chapter");
        return hexIdForChapterId(chapter.id());
    }

    public String hexIdForChapterId(String id) {
        return lookup(chapterIds, id, "chapter");
    }

    public String hexIdForQuest(Quest quest) {
        Objects.requireNonNull(quest, "quest");
        return hexIdForQuestId(quest.id());
    }

    public String hexIdForQuestId(String id) {
        return lookup(questIds, id, "quest");
    }

    public String hexIdForLootTable(LootTable lootTable) {
        Objects.requireNonNull(lootTable, "lootTable");
        return hexIdForLootTableId(lootTable.id());
    }

    public String hexIdForLootTableId(String id) {
        return lookup(lootTableIds, id, "table");
    }

    @Deprecated
    public long longIdForChapterGroup(ChapterGroup group) {
        return parseHexAsLong(hexIdForChapterGroup(group));
    }

    @Deprecated
    public long longIdForChapterGroupId(String id) {
        return parseHexAsLong(hexIdForChapterGroupId(id));
    }

    @Deprecated
    public long longIdForChapter(Chapter chapter) {
        return parseHexAsLong(hexIdForChapter(chapter));
    }

    @Deprecated
    public long longIdForChapterId(String id) {
        return parseHexAsLong(hexIdForChapterId(id));
    }

    @Deprecated
    public long longIdForQuest(Quest quest) {
        return parseHexAsLong(hexIdForQuest(quest));
    }

    @Deprecated
    public long longIdForQuestId(String id) {
        return parseHexAsLong(hexIdForQuestId(id));
    }

    @Deprecated
    public long longIdForLootTable(LootTable lootTable) {
        return parseHexAsLong(hexIdForLootTable(lootTable));
    }

    @Deprecated
    public long longIdForLootTableId(String id) {
        return parseHexAsLong(hexIdForLootTableId(id));
    }

    private long parseHexAsLong(String hexId) {
        return Long.parseUnsignedLong(hexId, 16);
    }
}
