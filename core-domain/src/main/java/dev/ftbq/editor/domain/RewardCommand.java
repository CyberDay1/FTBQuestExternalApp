package dev.ftbq.editor.domain;

import java.util.Objects;

/**
 * Encapsulates a command reward configuration.
 */
public final class RewardCommand {

    private final String command;
    private final boolean runAsServer;

    public RewardCommand(String command, boolean runAsServer) {
        this.command = Objects.requireNonNull(command, "command").trim();
        this.runAsServer = runAsServer;
        if (this.command.isEmpty()) {
            throw new IllegalArgumentException("Command reward must include a command");
        }
    }

    public String command() {
        return command;
    }

    public boolean runAsServer() {
        return runAsServer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RewardCommand that)) return false;
        return runAsServer == that.runAsServer && command.equals(that.command);
    }

    @Override
    public int hashCode() {
        return Objects.hash(command, runAsServer);
    }

    @Override
    public String toString() {
        return "RewardCommand{" +
                "command='" + command + '\'' +
                ", runAsServer=" + runAsServer +
                '}';
    }
}
