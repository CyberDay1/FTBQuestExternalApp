package dev.ftbq.editor.domain;

import java.util.Map;
import java.util.Objects;

/**
 * Extension point for mod-provided rewards.
 */
public record CustomReward(String type, Map<String, Object> metadata) implements Reward {

    public CustomReward {
        Objects.requireNonNull(type, "type");
        metadata = Map.copyOf(Objects.requireNonNull(metadata, "metadata"));
    }

    @Override
    public Map<String, Object> metadata() {
        return metadata;
    }
}
