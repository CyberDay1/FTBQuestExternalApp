package dev.ftbq.editor.domain;

import java.util.Locale;

/**
 * Distinguishes the available kinds of quest rewards.
 */
public enum RewardType {
    ITEM("item", "Item"),
    LOOT_TABLE("loot", "Loot Table"),
    XP_LEVELS("xp_levels", "XP Levels"),
    XP_AMOUNT("xp", "XP Amount"),
    COMMAND("command", "Command"),
    CHOICE("choice", "Choice"),
    RANDOM("random", "Random"),
    ADVANCEMENT("advancement", "Advancement"),
    STAGE("gamestage", "GameStage"),
    TOAST("toast", "Toast"),
    CUSTOM("custom", "Custom"),
    ALL_TABLE("all_table", "All Table");

    private final String id;
    private final String displayName;

    RewardType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public static RewardType fromId(String id) {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (RewardType type : values()) {
            if (type.id.equals(normalized)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown reward type id: " + id);
    }
}
