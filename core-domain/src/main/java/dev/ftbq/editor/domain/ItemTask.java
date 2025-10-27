package dev.ftbq.editor.domain;

import java.util.Objects;

/**
 * Requires the player to submit an item.
 */
public record ItemTask(ItemRef item, boolean consume) implements Task {

    public ItemTask {
        Objects.requireNonNull(item, "item");
    }

    public ItemTask(String itemId, int count) {
        this(new ItemRef(itemId, count), true);
    }

    public ItemTask(String itemId, int count, boolean consume) {
        this(new ItemRef(itemId, count), consume);
    }

    @Override
    public String type() {
        return "item";
    }

    @Override
    public String describe() {
        String action = consume ? "Submit" : "Have";
        return action + " " + item.count() + " × " + item.itemId();
    }
}
