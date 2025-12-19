package dev.ftbq.editor.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Registry for mapping reward type identifiers to their implementation classes.
 */
public final class RewardTypeRegistry {

    private static final Map<String, Class<? extends Reward>> TYPES = new LinkedHashMap<>();
    private static final Map<Class<? extends Reward>, String> IDS = new LinkedHashMap<>();

    static {
        register("item", ItemReward.class);
        register("xp_levels", XpLevelReward.class);
        register("xp_level", XpLevelReward.class);
        register("xp", XpReward.class);
        register("xp_amount", XpReward.class);
        register("loot_table", LootTableReward.class);
        register("command", CommandReward.class);
        register("choice", ChoiceReward.class);
        register("random", RandomReward.class);
        register("advancement", AdvancementReward.class);
        register("gamestage", StageReward.class);
        register("stage", StageReward.class);
        register("toast", ToastReward.class);
        register("custom", CustomReward.class);
        register("all_table", AllTableReward.class);
    }

    private RewardTypeRegistry() {
    }

    public static synchronized void register(String id, Class<? extends Reward> type) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        String normalized = id.trim().toLowerCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Reward id cannot be blank");
        }
        TYPES.put(normalized, type);
        IDS.put(type, normalized);
    }

    public static Set<String> ids() {
        return Collections.unmodifiableSet(TYPES.keySet());
    }

    public static Optional<Class<? extends Reward>> typeFor(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(TYPES.get(id.trim().toLowerCase()));
    }

    public static Optional<String> idFor(Class<? extends Reward> type) {
        return Optional.ofNullable(IDS.get(type));
    }
}
