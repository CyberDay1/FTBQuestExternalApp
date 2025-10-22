package dev.ftbq.editor.domain;

import java.util.Objects;

/**
 * Dependency linking quests together.
 */
public record Dependency(String questId, boolean required) {

    public Dependency {
        Objects.requireNonNull(questId, "questId");
    }
}
