package dev.ftbq.editor.domain;

import java.util.Map;

/**
 * A reward granted when completing a quest.
 */
public sealed interface Reward permits ItemReward, XpReward, CommandReward, CustomReward {

    String type();

    default Map<String, Object> metadata() {
        return Map.of();
    }
}
