package dev.ftbq.editor.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A single quest players can complete.
 */
public record Quest(String id,
                    String title,
                    String description,
                    IconRef icon,
                    List<Task> tasks,
                    List<Reward> rewards,
                    List<Dependency> dependencies,
                    Visibility visibility) {

    public Quest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(visibility, "visibility");
        tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
        rewards = List.copyOf(Objects.requireNonNull(rewards, "rewards"));
        dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private String title;
        private String description = "";
        private IconRef icon = new IconRef("minecraft:book");
        private final List<Task> tasks = new ArrayList<>();
        private final List<Reward> rewards = new ArrayList<>();
        private final List<Dependency> dependencies = new ArrayList<>();
        private Visibility visibility = Visibility.VISIBLE;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
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

        public Builder addTask(Task task) {
            this.tasks.add(Objects.requireNonNull(task, "task"));
            return this;
        }

        public Builder tasks(List<Task> tasks) {
            this.tasks.clear();
            this.tasks.addAll(Objects.requireNonNull(tasks, "tasks"));
            return this;
        }

        public Builder addReward(Reward reward) {
            this.rewards.add(Objects.requireNonNull(reward, "reward"));
            return this;
        }

        public Builder rewards(List<Reward> rewards) {
            this.rewards.clear();
            this.rewards.addAll(Objects.requireNonNull(rewards, "rewards"));
            return this;
        }

        public Builder addDependency(Dependency dependency) {
            this.dependencies.add(Objects.requireNonNull(dependency, "dependency"));
            return this;
        }

        public Builder dependencies(List<Dependency> dependencies) {
            this.dependencies.clear();
            this.dependencies.addAll(Objects.requireNonNull(dependencies, "dependencies"));
            return this;
        }

        public Quest build() {
            return new Quest(id, title, description, icon, tasks, rewards, dependencies, visibility);
        }
    }
}
