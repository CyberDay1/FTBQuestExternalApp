package dev.ftbq.editor.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A collection of related quests.
 */
public record Chapter(String id,
                      String title,
                      IconRef icon,
                      BackgroundRef background,
                      List<Quest> quests,
                      Visibility visibility,
                      List<ChapterImage> images,
                      List<QuestLink> questLinks) {

    public Chapter {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(visibility, "visibility");
        quests = List.copyOf(Objects.requireNonNull(quests, "quests"));
        if (images == null) {
            images = List.of();
        } else {
            images = List.copyOf(images);
        }
        if (questLinks == null) {
            questLinks = List.of();
        } else {
            questLinks = List.copyOf(questLinks);
        }
    }

    public Chapter(String id, String title, IconRef icon, BackgroundRef background,
                   List<Quest> quests, Visibility visibility, List<ChapterImage> images) {
        this(id, title, icon, background, quests, visibility, images, List.of());
    }

    public Chapter(String id, String title, IconRef icon, BackgroundRef background,
                   List<Quest> quests, Visibility visibility) {
        this(id, title, icon, background, quests, visibility, List.of(), List.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String title;
        private IconRef icon = new IconRef("minecraft:book");
        private BackgroundRef background = new BackgroundRef("minecraft:textures/gui/default.png");
        private final List<Quest> quests = new ArrayList<>();
        private Visibility visibility = Visibility.VISIBLE;
        private final List<ChapterImage> images = new ArrayList<>();
        private final List<QuestLink> questLinks = new ArrayList<>();

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

        public Builder background(BackgroundRef background) {
            this.background = Objects.requireNonNull(background, "background");
            return this;
        }

        public Builder visibility(Visibility visibility) {
            this.visibility = Objects.requireNonNull(visibility, "visibility");
            return this;
        }

        public Builder addQuest(Quest quest) {
            this.quests.add(Objects.requireNonNull(quest, "quest"));
            return this;
        }

        public Builder quests(List<Quest> quests) {
            this.quests.clear();
            this.quests.addAll(Objects.requireNonNull(quests, "quests"));
            return this;
        }

        public Builder addImage(ChapterImage image) {
            this.images.add(Objects.requireNonNull(image, "image"));
            return this;
        }

        public Builder images(List<ChapterImage> images) {
            this.images.clear();
            if (images != null) {
                this.images.addAll(images);
            }
            return this;
        }

        public Builder addQuestLink(QuestLink link) {
            this.questLinks.add(Objects.requireNonNull(link, "link"));
            return this;
        }

        public Builder questLinks(List<QuestLink> questLinks) {
            this.questLinks.clear();
            if (questLinks != null) {
                this.questLinks.addAll(questLinks);
            }
            return this;
        }

        public Chapter build() {
            return new Chapter(id, title, icon, background, quests, visibility, images, questLinks);
        }
    }
}
