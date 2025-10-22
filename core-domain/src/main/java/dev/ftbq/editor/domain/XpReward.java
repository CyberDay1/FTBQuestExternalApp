package dev.ftbq.editor.domain;

/**
 * Grants experience points.
 */
public record XpReward(int amount) implements Reward {

    @Override
    public String type() {
        return "xp";
    }
}
