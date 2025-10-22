package dev.ftbq.editor.domain;

import java.util.Objects;

/**
 * Requires the player to submit an item.
 */
public record ItemTask(ItemRef item, boolean consume) implements Task {

    public ItemTask {
        Objects.requireNonNull(item, "item");
    }

    @Override
    public String type() {
        return "item";
    }
}
