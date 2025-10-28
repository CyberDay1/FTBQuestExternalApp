package dev.ftbq.editor.services.generator;

import java.util.List;
import java.util.Objects;

/**
 * Captures the desired mod integration goals for the generated chapters.
 */
public record ModIntent(String modId,
                        List<String> features,
                        String progressionNotes,
                        List<String> exampleReferences) {

    public ModIntent {
        Objects.requireNonNull(modId, "modId");
        features = List.copyOf(Objects.requireNonNull(features, "features"));
        Objects.requireNonNull(progressionNotes, "progressionNotes");
        exampleReferences = List.copyOf(Objects.requireNonNull(exampleReferences, "exampleReferences"));
    }
}
