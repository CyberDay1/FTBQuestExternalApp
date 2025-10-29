package dev.ftbq.editor.io.snbt;

import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.ChapterGroup;
import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Generates deterministic numeric IDs for SNBT export to match FTB Quests expectations.
 */
public final class SnbtIdRegistry {

    private final Map<String, Long> chapterGroupIds = new LinkedHashMap<>();
    private final Map<String, Long> chapterIds = new LinkedHashMap<>();
    private final Map<String, Long> questIds = new LinkedHashMap<>();
    private final Map<String, Long> lootTableIds = new LinkedHashMap<>();
    private long nextId = 1L;

    public SnbtIdRegistry(QuestFile questFile) {
        Objects.requireNonNull(questFile, "questFile");
        questFile.chapterGroups().forEach(group -> register(chapterGroupIds, group.id()));
        questFile.chapters().forEach(chapter -> register(chapterIds, chapter.id()));
        questFile.chapters().forEach(chapter ->
                chapter.quests().forEach(quest -> register(questIds, quest.id())));
        questFile.lootTables().forEach(table -> register(lootTableIds, table.id()));
    }

    private void register(Map<String, Long> map, String id) {
        map.computeIfAbsent(id, key -> nextId++);
    }

    private long lookup(Map<String, Long> map, String id) {
        Objects.requireNonNull(id, "id");
        return map.computeIfAbsent(id, key -> nextId++);
    }

    public long longIdForChapterGroup(ChapterGroup group) {
        Objects.requireNonNull(group, "group");
        return longIdForChapterGroupId(group.id());
    }

    public long longIdForChapterGroupId(String id) {
        return lookup(chapterGroupIds, id);
    }

    public long longIdForChapter(Chapter chapter) {
        Objects.requireNonNull(chapter, "chapter");
        return longIdForChapterId(chapter.id());
    }

    public long longIdForChapterId(String id) {
        return lookup(chapterIds, id);
    }

    public long longIdForQuest(Quest quest) {
        Objects.requireNonNull(quest, "quest");
        return longIdForQuestId(quest.id());
    }

    public long longIdForQuestId(String id) {
        return lookup(questIds, id);
    }

    public long longIdForLootTable(LootTable lootTable) {
        Objects.requireNonNull(lootTable, "lootTable");
        return longIdForLootTableId(lootTable.id());
    }

    public long longIdForLootTableId(String id) {
        return lookup(lootTableIds, id);
    }
}
