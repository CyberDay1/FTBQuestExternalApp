package dev.ftbq.editor.services.events;

import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.services.bus.Event;

import java.util.Objects;

public record QuestChanged(Quest quest) implements Event {
    public QuestChanged {
        Objects.requireNonNull(quest, "quest");
    }
}
