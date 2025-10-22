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
                      Visibility visibility) {

    public Chapter {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(visibility, "visibility");
        quests = List.copyOf(Objects.requireNonNull(quests, "quests"));
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

        public Chapter build() {
            return new Chapter(id, title, icon, background, quests, visibility);
        }
    }
}
