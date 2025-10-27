package dev.ftbq.editor.domain;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a quest reward. Each reward type exposes a relevant payload such as an item,
 * loot table identifier, experience amount, or command configuration.
 */
public final class Reward {

    private final RewardType type;
    private final ItemRef item;
    private final String lootTableId;
    private final Integer experience;
    private final RewardCommand command;

    private Reward(RewardType type, ItemRef item, String lootTableId, Integer experience, RewardCommand command) {
        this.type = Objects.requireNonNull(type, "type");
        this.item = item;
        this.lootTableId = lootTableId;
        this.experience = experience;
        this.command = command;
        validateState();
    }

    public static Reward item(ItemRef item) {
        return new Reward(RewardType.ITEM, Objects.requireNonNull(item, "item"), null, null, null);
    }

    public static Reward lootTable(String lootTableId) {
        if (lootTableId == null || lootTableId.isBlank()) {
            throw new IllegalArgumentException("Loot table reward requires a loot table id");
        }
        return new Reward(RewardType.LOOT_TABLE, null, lootTableId.trim(), null, null);
    }

    public static Reward experience(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Experience reward cannot be negative");
        }
        return new Reward(RewardType.EXPERIENCE, null, null, amount, null);
    }

    public static Reward command(RewardCommand command) {
        return new Reward(RewardType.COMMAND, null, null, null, Objects.requireNonNull(command, "command"));
    }

    private void validateState() {
        switch (type) {
            case ITEM -> {
                if (item == null) {
                    throw new IllegalStateException("Item reward must include an item reference");
                }
            }
            case LOOT_TABLE -> {
                if (lootTableId == null) {
                    throw new IllegalStateException("Loot table reward must include a loot table id");
                }
            }
            case EXPERIENCE -> {
                if (experience == null) {
                    throw new IllegalStateException("Experience reward must include an amount");
                }
            }
            case COMMAND -> {
                if (command == null) {
                    throw new IllegalStateException("Command reward must include a command configuration");
                }
            }
        }
    }

    public RewardType type() {
        return type;
    }

    public Optional<ItemRef> item() {
        return Optional.ofNullable(item);
    }

    public Optional<String> lootTableId() {
        return Optional.ofNullable(lootTableId);
    }

    public Optional<Integer> experience() {
        return Optional.ofNullable(experience);
    }

    public Optional<RewardCommand> command() {
        return Optional.ofNullable(command);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Reward reward)) return false;
        return type == reward.type &&
                Objects.equals(item, reward.item) &&
                Objects.equals(lootTableId, reward.lootTableId) &&
                Objects.equals(experience, reward.experience) &&
                Objects.equals(command, reward.command);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, item, lootTableId, experience, command);
    }

    @Override
    public String toString() {
        return "Reward{" +
                "type=" + type +
                ", item=" + item +
                ", lootTableId='" + lootTableId + '\'' +
                ", experience=" + experience +
                ", command=" + command +
                '}';
    }
}
