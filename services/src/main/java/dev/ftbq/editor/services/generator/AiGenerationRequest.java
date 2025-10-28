package dev.ftbq.editor.services.generator;

import java.util.Objects;

/**
 * Bundles the prompt and supplemental context passed to AI model providers.
 */
public record AiGenerationRequest(ModelPrompt prompt,
                                  ModSelection modSelection,
                                  RewardConfiguration rewardConfiguration,
                                  QuestLimits questLimits) {

    public AiGenerationRequest {
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(modSelection, "modSelection");
        Objects.requireNonNull(rewardConfiguration, "rewardConfiguration");
        Objects.requireNonNull(questLimits, "questLimits");
    }
}
