package dev.ftbq.editor.services.events;

import dev.ftbq.editor.domain.Quest;

import java.util.Objects;

public record QuestChanged(Quest quest) {
    public QuestChanged {
        Objects.requireNonNull(quest, "quest");
    }
}
