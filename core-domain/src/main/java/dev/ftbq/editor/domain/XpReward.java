package dev.ftbq.editor.domain;

import java.util.Optional;

public record XpReward(int amount) implements Reward {

    public XpReward {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must be non-negative");
        }
    }

    @Override
    public RewardType type() {
        return RewardType.XP_AMOUNT;
    }

    @Override
    public Optional<Integer> experienceAmount() {
        return Optional.of(amount);
    }

    @Override
    public String describe() {
        return amount + " xp";
    }
}
