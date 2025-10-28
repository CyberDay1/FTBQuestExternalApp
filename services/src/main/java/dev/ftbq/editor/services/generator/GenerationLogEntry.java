package dev.ftbq.editor.services.generator;

import java.time.Instant;
import java.util.Objects;

/**
 * Structured log entry describing an action taken during generation.
 */
public record GenerationLogEntry(String stage, String message, Instant timestamp) {

    public GenerationLogEntry {
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(message, "message");
        timestamp = Objects.requireNonNullElse(timestamp, Instant.now());
    }

    public static GenerationLogEntry of(String stage, String message) {
        return new GenerationLogEntry(stage, message, Instant.now());
    }
}
