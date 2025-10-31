package dev.ftbq.editor.store;

import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;

import java.util.List;
import java.util.Objects;

/**
 * Represents the quest project currently loaded in the editor UI.
 */
public final class Project {
    private final QuestFile questFile;
    private final List<Chapter> chapters;
    private final List<Quest> quests;
    private final List<LootTable> lootTables;

    public Project(QuestFile questFile) {
        this.questFile = Objects.requireNonNull(questFile, "questFile");
        this.chapters = List.copyOf(questFile.chapters());
        this.lootTables = List.copyOf(questFile.lootTables());
        this.quests = this.chapters.stream()
                .flatMap(chapter -> chapter.quests().stream())
                .toList();
    }

    public QuestFile getQuestFile() {
        return questFile;
    }

    public List<Chapter> getChapters() {
        return chapters;
    }

    public List<Quest> getQuests() {
        return quests;
    }

    public List<LootTable> getLootTables() {
        return lootTables;
    }
}
