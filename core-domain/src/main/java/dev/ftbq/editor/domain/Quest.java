package dev.ftbq.editor.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A single quest players can complete.
 */
public record Quest(String id,
                    String title,
                    String description,
                    IconRef icon,
                    List<Task> tasks,
                    List<ItemReward> itemRewards,
                    Integer experienceAmount,
                    Integer experienceLevels,
                    String lootTableId,
                    RewardCommand commandReward,
                    List<Dependency> dependencies,
                    Visibility visibility) {

    public Quest {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(visibility, "visibility");
        tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
        itemRewards = List.copyOf(Objects.requireNonNull(itemRewards, "itemRewards"));
        dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies"));
        if (experienceAmount != null && experienceAmount < 0) {
            throw new IllegalArgumentException("experienceAmount must be >= 0");
        }
        if (experienceLevels != null && experienceLevels < 0) {
            throw new IllegalArgumentException("experienceLevels must be >= 0");
        }
        if (experienceAmount != null && experienceLevels != null) {
            throw new IllegalArgumentException("Quest cannot grant both XP amount and XP levels");
        }
    }

    public List<Reward> rewards() {
        List<Reward> rewards = new ArrayList<>();
        for (ItemReward itemReward : itemRewards) {
            rewards.add(itemReward);
        }
        if (experienceLevels != null) {
            rewards.add(new XpLevelReward(experienceLevels));
        }
        if (experienceAmount != null) {
            rewards.add(new XpReward(experienceAmount));
        }
        if (lootTableId != null && !lootTableId.isBlank()) {
            rewards.add(new LootTableReward(lootTableId));
        }
        if (commandReward != null) {
            rewards.add(new CommandReward(commandReward));
        }
        return List.copyOf(rewards);
    }

    public Optional<Integer> experienceAmountOptional() {
        return Optional.ofNullable(experienceAmount);
    }

    public Optional<Integer> experienceLevelsOptional() {
        return Optional.ofNullable(experienceLevels);
    }

    public Optional<String> lootTableIdOptional() {
        return Optional.ofNullable(lootTableId);
    }

    public Optional<RewardCommand> commandRewardOptional() {
        return Optional.ofNullable(commandReward);
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
        private final List<ItemReward> itemRewards = new ArrayList<>();
        private Integer experienceAmount;
        private Integer experienceLevels;
        private String lootTableId;
        private RewardCommand commandReward;
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
            Objects.requireNonNull(reward, "reward");
            switch (reward.type()) {
                case ITEM -> {
                    if (reward instanceof ItemReward itemReward) {
                        addItemReward(itemReward);
                    } else {
                        reward.item().ifPresent(item -> addItemReward(new ItemReward(item)));
                    }
                }
                case LOOT_TABLE -> lootTableId(reward.lootTableId().orElse(null));
                case XP_LEVELS -> experienceLevels(reward.experienceLevels().orElse(null));
                case XP_AMOUNT -> experienceAmount(reward.experienceAmount().orElse(null));
                case COMMAND -> reward.command().ifPresent(this::commandReward);
            }
            return this;
        }

        public Builder rewards(List<Reward> rewards) {
            Objects.requireNonNull(rewards, "rewards");
            clearRewardFields();
            rewards.forEach(this::addReward);
            return this;
        }

        public Builder addItemReward(ItemReward reward) {
            Objects.requireNonNull(reward, "reward");
            this.itemRewards.add(reward);
            return this;
        }

        public Builder itemRewards(List<ItemReward> rewards) {
            this.itemRewards.clear();
            this.itemRewards.addAll(Objects.requireNonNull(rewards, "rewards"));
            return this;
        }

        public Builder experienceAmount(Integer amount) {
            if (amount != null && amount < 0) {
                throw new IllegalArgumentException("experienceAmount must be >= 0");
            }
            this.experienceAmount = amount;
            if (amount != null) {
                this.experienceLevels = null;
            }
            return this;
        }

        public Builder experienceLevels(Integer levels) {
            if (levels != null && levels < 0) {
                throw new IllegalArgumentException("experienceLevels must be >= 0");
            }
            this.experienceLevels = levels;
            if (levels != null) {
                this.experienceAmount = null;
            }
            return this;
        }

        public Builder lootTableId(String lootTableId) {
            this.lootTableId = lootTableId;
            return this;
        }

        public Builder commandReward(RewardCommand commandReward) {
            this.commandReward = commandReward;
            return this;
        }

        public Builder commandReward(String command) {
            if (command == null || command.isBlank()) {
                this.commandReward = null;
            } else {
                this.commandReward = new RewardCommand(command, false);
            }
            return this;
        }

        private void clearRewardFields() {
            this.itemRewards.clear();
            this.experienceAmount = null;
            this.experienceLevels = null;
            this.lootTableId = null;
            this.commandReward = null;
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
            return new Quest(id, title, description, icon, tasks, itemRewards, experienceAmount,
                    experienceLevels, lootTableId, commandReward, dependencies, visibility);
        }
    }
}
