package dev.ftbq.editor.domain;

import java.util.Objects;

/**
 * Requires completion of a Minecraft advancement.
 */
public record AdvancementTask(String advancementId) implements Task {

    public AdvancementTask {
        Objects.requireNonNull(advancementId, "advancementId");
    }

    @Override
    public String type() {
        return "advancement";
    }

    @Override
    public String describe() {
        return "Complete advancement " + advancementId;
    }
}
