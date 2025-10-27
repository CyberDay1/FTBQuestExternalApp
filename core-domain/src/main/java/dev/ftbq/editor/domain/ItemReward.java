package dev.ftbq.editor.domain;

import java.util.Objects;
import java.util.Optional;

public record ItemReward(ItemRef itemRef) implements Reward {

    public ItemReward {
        Objects.requireNonNull(itemRef, "item");
    }

    @Override
    public RewardType type() {
        return RewardType.ITEM;
    }

    @Override
    public Optional<ItemRef> item() {
        return Optional.of(itemRef);
    }

    @Override
    public String describe() {
        return itemRef.count() + " × " + itemRef.itemId();
    }
}
