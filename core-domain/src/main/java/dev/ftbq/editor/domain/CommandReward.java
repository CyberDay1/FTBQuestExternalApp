package dev.ftbq.editor.domain;

import java.util.Objects;

/**
 * Executes a command when granted.
 */
public record CommandReward(String command, boolean asPlayer) implements Reward {

    public CommandReward {
        Objects.requireNonNull(command, "command");
    }

    @Override
    public String type() {
        return "command";
    }
}
