package dev.ftbq.editor.domain;

import java.util.Objects;

/**
 * Gives a player items.
 */
public record ItemReward(ItemRef item) implements Reward {

    public ItemReward {
        Objects.requireNonNull(item, "item");
    }

    @Override
    public String type() {
        return "item";
    }
}
