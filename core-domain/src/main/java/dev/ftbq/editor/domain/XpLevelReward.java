package dev.ftbq.editor.domain;

import java.util.Optional;

public record XpLevelReward(int levels) implements Reward {

    public XpLevelReward {
        if (levels < 0) {
            throw new IllegalArgumentException("levels must be non-negative");
        }
    }

    @Override
    public RewardType type() {
        return RewardType.XP_LEVELS;
    }

    @Override
    public Optional<Integer> experienceLevels() {
        return Optional.of(levels);
    }

    @Override
    public String describe() {
        return levels + " levels";
    }
}
