package dev.ftbq.editor.services.ai;

import dev.ftbq.editor.services.generator.ModIntent;
import dev.ftbq.editor.services.generator.QuestDesignSpec;
import java.util.Objects;

/**
 * Coordinates prompt construction and parsing of model output for quest chapter drafts.
 */
public final class QuestGenerationService {

    private final OpenAIClient openAIClient;

    public QuestGenerationService() {
        this(new OpenAIClient());
    }

    public QuestGenerationService(OpenAIClient openAIClient) {
        this.openAIClient = Objects.requireNonNull(openAIClient, "openAIClient");
    }

    /**
     * Generates a quest chapter draft in SNBT form based on the provided design specification and mod intent.
     *
     * @param designSpec desired chapter parameters
     * @param modIntent mod integration requirements
     * @return SNBT text describing the generated chapter
     */
    public String generateChapterDraft(QuestDesignSpec designSpec, ModIntent modIntent) {
        Objects.requireNonNull(designSpec, "designSpec");
        Objects.requireNonNull(modIntent, "modIntent");

        String prompt = buildPrompt(designSpec, modIntent);
        String aiOutput = openAIClient.generateQuestChapter(prompt);
        return extractSnbt(aiOutput);
    }

    private String buildPrompt(QuestDesignSpec designSpec, ModIntent modIntent) {
        StringBuilder builder = new StringBuilder();
        builder.append("Design a new FTB Quests chapter using SNBT syntax. Focus on the ")
                .append(designSpec.theme())
                .append(" theme while honouring the pack's constraints.\n\n");

        builder.append("Chapter requirements:\n");
        builder.append("- Total quests: ").append(designSpec.chapterLength()).append('\n');
        builder.append("- Difficulty curve: ")
                .append(String.join(" → ", designSpec.difficultyCurve()))
                .append('\n');
        builder.append("- Gating rules: ")
                .append(String.join(", ", designSpec.gatingRules()))
                .append('\n');
        builder.append("- Progression axes: ")
                .append(String.join(" · ", designSpec.progressionAxes()))
                .append('\n');
        builder.append("- Reward budget: ")
                .append(designSpec.rewardBudget())
                .append('\n');
        builder.append("- Allowed task types: ")
                .append(String.join(", ", designSpec.allowedTasks()))
                .append('\n');
        if (!designSpec.itemBlacklist().isEmpty()) {
            builder.append("- Do not use items: ")
                    .append(String.join(", ", designSpec.itemBlacklist()))
                    .append('\n');
        }
        builder.append('\n');

        builder.append("Mod integration targets:\n");
        builder.append("- Mod: ").append(modIntent.modId()).append('\n');
        if (!modIntent.features().isEmpty()) {
            builder.append("- Key features: ")
                    .append(String.join(", ", modIntent.features()))
                    .append('\n');
        }
        if (!modIntent.progressionNotes().isBlank()) {
            builder.append("- Progression notes: ")
                    .append(modIntent.progressionNotes())
                    .append('\n');
        }
        if (!modIntent.exampleReferences().isEmpty()) {
            builder.append("- Reference builds: ")
                    .append(String.join(", ", modIntent.exampleReferences()))
                    .append('\n');
        }
        builder.append('\n');

        builder.append("Output expectations:\n")
                .append("- Respond with a single SNBT chapter object.\n")
                .append("- Preserve canonical quest key order and include dependencies.\n")
                .append("- Use lower_snake_case identifiers and align icons with the mod when possible.\n")
                .append("- Avoid commentary or Markdown code fences in the response.\n");

        return builder.toString();
    }

    private String extractSnbt(String aiOutput) {
        String normalized = Objects.requireNonNull(aiOutput, "aiOutput").replace("\r\n", "\n").trim();
        if (normalized.isEmpty()) {
            throw new IllegalStateException("Model output was empty.");
        }

        int fenceStart = normalized.indexOf("```");
        if (fenceStart >= 0) {
            int contentStart = normalized.indexOf('\n', fenceStart + 3);
            if (contentStart >= 0) {
                int fenceEnd = normalized.indexOf("```", contentStart + 1);
                if (fenceEnd > contentStart) {
                    String fenced = normalized.substring(contentStart + 1, fenceEnd).trim();
                    if (!fenced.isEmpty()) {
                        return fenced;
                    }
                }
            }
        }

        if (normalized.regionMatches(true, 0, "snbt", 0, 4)) {
            int colon = normalized.indexOf(':');
            if (colon > 0 && colon + 1 < normalized.length()) {
                return normalized.substring(colon + 1).trim();
            }
        }
        return normalized;
    }
}
