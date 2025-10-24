package dev.ftbq.editor.services.events;

import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.services.bus.Event;

import java.nio.file.Path;
import java.util.Objects;

public record PackReloaded(Path packRoot, QuestFile questFile) implements Event {
    public PackReloaded {
        Objects.requireNonNull(packRoot, "packRoot");
        Objects.requireNonNull(questFile, "questFile");
    }
}
