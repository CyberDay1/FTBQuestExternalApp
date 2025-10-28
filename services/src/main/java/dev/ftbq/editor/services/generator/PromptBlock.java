package dev.ftbq.editor.services.generator;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A typed block of text that becomes part of the model prompt.
 */
public record PromptBlock(PromptRole role,
                          String content,
                          Map<String, String> metadata) {

    public PromptBlock {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
        metadata = metadata == null ? Map.of() : Collections.unmodifiableMap(metadata);
    }

    public static PromptBlock of(PromptRole role, String content) {
        return new PromptBlock(role, content, Map.of());
    }

    public Optional<String> metadata(String key) {
        return Optional.ofNullable(metadata.get(key));
    }
}
