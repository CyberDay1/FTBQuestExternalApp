package dev.ftbq.editor.domain;

import java.util.Optional;

public record CommandReward(RewardCommand commandConfig) implements Reward {

    public CommandReward {
        if (commandConfig == null) {
            throw new IllegalArgumentException("command must not be null");
        }
    }

    @Override
    public RewardType type() {
        return RewardType.COMMAND;
    }

    @Override
    public Optional<RewardCommand> command() {
        return Optional.of(commandConfig);
    }

    @Override
    public String describe() {
        return commandConfig.command();
    }
}
