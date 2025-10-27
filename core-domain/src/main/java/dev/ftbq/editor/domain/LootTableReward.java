package dev.ftbq.editor.domain;

import java.util.Optional;

public record LootTableReward(String tableId) implements Reward {

    public LootTableReward {
        if (tableId == null || tableId.isBlank()) {
            throw new IllegalArgumentException("lootTableId must be provided");
        }
    }

    @Override
    public RewardType type() {
        return RewardType.LOOT_TABLE;
    }

    @Override
    public Optional<String> lootTableId() {
        return Optional.of(tableId);
    }

    @Override
    public String describe() {
        return tableId;
    }
}
