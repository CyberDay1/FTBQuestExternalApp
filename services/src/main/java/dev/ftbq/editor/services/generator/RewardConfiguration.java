package dev.ftbq.editor.services.generator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Captures reward preferences supplied alongside an AI generation request.
 */
public final class RewardConfiguration {

    private final boolean allowItemRewards;
    private final boolean allowXpRewards;
    private final boolean allowLootTableRewards;
    private final List<String> preferredLootTables;

    public RewardConfiguration(boolean allowItemRewards,
                               boolean allowXpRewards,
                               boolean allowLootTableRewards,
                               List<String> preferredLootTables) {
        Objects.requireNonNull(preferredLootTables, "preferredLootTables");
        this.allowItemRewards = allowItemRewards;
        this.allowXpRewards = allowXpRewards;
        this.allowLootTableRewards = allowLootTableRewards;
        this.preferredLootTables = Collections.unmodifiableList(new ArrayList<>(preferredLootTables));
    }

    public static RewardConfiguration allowAll() {
        return new RewardConfiguration(true, true, true, List.of());
    }

    public boolean allowItemRewards() {
        return allowItemRewards;
    }

    public boolean allowXpRewards() {
        return allowXpRewards;
    }

    public boolean allowLootTableRewards() {
        return allowLootTableRewards;
    }

    public List<String> preferredLootTables() {
        return preferredLootTables;
    }

    public boolean hasAnyRewardTypeEnabled() {
        return allowItemRewards || allowXpRewards || allowLootTableRewards;
    }
}
