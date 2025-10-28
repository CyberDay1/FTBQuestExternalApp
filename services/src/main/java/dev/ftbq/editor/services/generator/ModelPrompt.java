package dev.ftbq.editor.services.generator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Ordered collection of prompt blocks passed to an AI provider.
 */
public final class ModelPrompt {
    private final List<PromptBlock> blocks;

    public ModelPrompt(List<PromptBlock> blocks) {
        Objects.requireNonNull(blocks, "blocks");
        this.blocks = Collections.unmodifiableList(new ArrayList<>(blocks));
    }

    public List<PromptBlock> blocks() {
        return blocks;
    }
}
