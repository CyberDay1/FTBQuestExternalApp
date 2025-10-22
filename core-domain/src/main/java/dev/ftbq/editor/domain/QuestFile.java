package dev.ftbq.editor.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Root container for quest content.
 */
public record QuestFile(String id,
                        String title,
                        List<ChapterGroup> chapterGroups,
                        List<Chapter> chapters,
                        List<LootTable> lootTables) {

    public QuestFile {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        chapterGroups = List.copyOf(Objects.requireNonNull(chapterGroups, "chapterGroups"));
        chapters = List.copyOf(Objects.requireNonNull(chapters, "chapters"));
        lootTables = List.copyOf(Objects.requireNonNull(lootTables, "lootTables"));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String title;
        private final List<ChapterGroup> chapterGroups = new ArrayList<>();
        private final List<Chapter> chapters = new ArrayList<>();
        private final List<LootTable> lootTables = new ArrayList<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder addChapterGroup(ChapterGroup group) {
            this.chapterGroups.add(Objects.requireNonNull(group, "group"));
            return this;
        }

        public Builder addChapter(Chapter chapter) {
            this.chapters.add(Objects.requireNonNull(chapter, "chapter"));
            return this;
        }

        public Builder chapterGroups(List<ChapterGroup> groups) {
            this.chapterGroups.clear();
            this.chapterGroups.addAll(Objects.requireNonNull(groups, "groups"));
            return this;
        }

        public Builder chapters(List<Chapter> chapters) {
            this.chapters.clear();
            this.chapters.addAll(Objects.requireNonNull(chapters, "chapters"));
            return this;
        }

        public Builder addLootTable(LootTable lootTable) {
            this.lootTables.add(Objects.requireNonNull(lootTable, "lootTable"));
            return this;
        }

        public Builder lootTables(List<LootTable> lootTables) {
            this.lootTables.clear();
            this.lootTables.addAll(Objects.requireNonNull(lootTables, "lootTables"));
            return this;
        }

        public QuestFile build() {
            return new QuestFile(id, title, chapterGroups, chapters, lootTables);
        }
    }
}
