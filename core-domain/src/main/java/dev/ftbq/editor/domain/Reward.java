package dev.ftbq.editor.domain;

import java.util.Optional;

/**
 * Represents a quest reward.
 */
public sealed interface Reward permits ItemReward, LootTableReward, XpLevelReward, XpReward, CommandReward {

    RewardType type();

    default Optional<ItemRef> item() {
        return Optional.empty();
    }

    default Optional<String> lootTableId() {
        return Optional.empty();
    }

    default Optional<Integer> experienceLevels() {
        return Optional.empty();
    }

    default Optional<Integer> experienceAmount() {
        return Optional.empty();
    }

    default Optional<RewardCommand> command() {
        return Optional.empty();
    }

    default String describe() {
        return switch (type()) {
            case ITEM -> item().map(item -> item.count() + " × " + item.itemId()).orElse("Item reward");
            case LOOT_TABLE -> lootTableId().orElse("Loot table reward");
            case XP_LEVELS -> experienceLevels().map(levels -> levels + " levels").orElse("XP levels");
            case XP_AMOUNT -> experienceAmount().map(amount -> amount + " xp").orElse("XP amount");
            case COMMAND -> command().map(RewardCommand::command).orElse("Command reward");
        };
    }

    static Reward item(ItemRef item) {
        return new ItemReward(item);
    }

    static Reward lootTable(String lootTableId) {
        return new LootTableReward(lootTableId);
    }

    static Reward xpLevels(int levels) {
        return new XpLevelReward(levels);
    }

    static Reward xpAmount(int amount) {
        return new XpReward(amount);
    }

    static Reward command(RewardCommand command) {
        return new CommandReward(command);
    }

    static Reward command(String command, boolean runAsServer) {
        return new CommandReward(new RewardCommand(command, runAsServer));
    }

    static Reward item(String itemId, int count) {
        return new ItemReward(new ItemRef(itemId, count));
    }
}
