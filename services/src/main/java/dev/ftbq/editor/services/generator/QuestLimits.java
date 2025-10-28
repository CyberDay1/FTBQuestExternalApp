package dev.ftbq.editor.services.generator;

/**
 * Represents quest count expectations and hard caps for generation runs.
 */
public record QuestLimits(int requestedCount, int hardCap) {

    public static final int MAX_AI_QUESTS = 30;

    public QuestLimits {
        if (requestedCount <= 0) {
            throw new IllegalArgumentException("requestedCount must be positive");
        }
        if (hardCap <= 0) {
            throw new IllegalArgumentException("hardCap must be positive");
        }
        if (requestedCount > hardCap) {
            throw new IllegalArgumentException("requestedCount cannot exceed hardCap");
        }
    }
}
