package dev.ftbq.editor.services.generator;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Response from a model provider containing the generated SNBT.
 */
public record ModelResponse(String content, Map<String, Object> diagnostics) {
    public ModelResponse {
        Objects.requireNonNull(content, "content");
        diagnostics = diagnostics == null ? Map.of() : Collections.unmodifiableMap(diagnostics);
    }
}
