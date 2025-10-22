package dev.ftbq.editor.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Logical grouping of chapters.
 */
public record ChapterGroup(String id,
                           String title,
                           IconRef icon,
                           List<String> chapterIds,
                           Visibility visibility) {

    public ChapterGroup {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(visibility, "visibility");
        chapterIds = List.copyOf(Objects.requireNonNull(chapterIds, "chapterIds"));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String title;
        private IconRef icon = new IconRef("minecraft:stone");
        private final List<String> chapterIds = new ArrayList<>();
        private Visibility visibility = Visibility.VISIBLE;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder icon(IconRef icon) {
            this.icon = Objects.requireNonNull(icon, "icon");
            return this;
        }

        public Builder visibility(Visibility visibility) {
            this.visibility = Objects.requireNonNull(visibility, "visibility");
            return this;
        }

        public Builder addChapterId(String chapterId) {
            this.chapterIds.add(Objects.requireNonNull(chapterId, "chapterId"));
            return this;
        }

        public Builder chapterIds(List<String> chapterIds) {
            this.chapterIds.clear();
            this.chapterIds.addAll(Objects.requireNonNull(chapterIds, "chapterIds"));
            return this;
        }

        public ChapterGroup build() {
            return new ChapterGroup(id, title, icon, chapterIds, visibility);
        }
    }
}
