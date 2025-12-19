package dev.ftbq.editor.services.ai;

import dev.ftbq.editor.services.generator.ModIntent;
import dev.ftbq.editor.services.generator.QuestDesignSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Coordinates prompt construction and parsing of model output for quest chapter drafts.
 */
public final class QuestGenerationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(QuestGenerationService.class);

    private final OpenAIClient openAIClient;
    private final OllamaClient ollamaClient;
    private AiProvider activeProvider;

    public QuestGenerationService() {
        this(createOpenAIClientSafe(), new OllamaClient());
    }

    private static OpenAIClient createOpenAIClientSafe() {
        try {
            return new OpenAIClient();
        } catch (Exception e) {
            LOGGER.debug("OpenAI client not available: {}", e.getMessage());
            return null;
        }
    }

    public QuestGenerationService(OpenAIClient openAIClient, OllamaClient ollamaClient) {
        this.openAIClient = openAIClient;
        this.ollamaClient = Objects.requireNonNull(ollamaClient, "ollamaClient");
        this.activeProvider = AiProvider.OLLAMA;
    }

    public void setActiveProvider(AiProvider provider) {
        this.activeProvider = Objects.requireNonNull(provider, "provider");
        LOGGER.info("AI provider set to: {}", provider.getDisplayName());
    }

    public AiProvider getActiveProvider() {
        return activeProvider;
    }

    public boolean isOllamaAvailable() {
        return ollamaClient.isAvailable();
    }

    public boolean isOpenAiConfigured() {
        try {
            dev.ftbq.editor.services.config.OpenAIConfig.getApiKey();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
    }

    public String getOllamaStatus() {
        String host = ollamaClient.getActiveHost();
        if (host != null) {
            return "Connected to " + host + " (model: " + ollamaClient.getModel() + ")";
        }
        return "Not available";
    }

    public String generateChapterDraft(QuestDesignSpec designSpec, ModIntent modIntent) {
        Objects.requireNonNull(designSpec, "designSpec");
        Objects.requireNonNull(modIntent, "modIntent");

        String userPrompt = buildPrompt(designSpec, modIntent);
        String aiOutput;

        if (activeProvider == AiProvider.OLLAMA) {
            LOGGER.info("Generating chapter using Ollama");
            aiOutput = ollamaClient.generate(FtbQuestPrompts.SYSTEM_PROMPT, userPrompt);
        } else {
            LOGGER.info("Generating chapter using OpenAI");
            if (openAIClient == null) {
                throw new IllegalStateException("OpenAI client not configured");
            }
            aiOutput = openAIClient.generateQuestChapter(userPrompt);
        }

        return extractSnbt(aiOutput);
    }

    public String generateWithCustomPrompt(String theme, int questCount, String additionalInstructions) {
        String userPrompt = FtbQuestPrompts.buildUserPrompt(theme, questCount, additionalInstructions);
        String aiOutput;

        if (activeProvider == AiProvider.OLLAMA) {
            aiOutput = ollamaClient.generate(FtbQuestPrompts.SYSTEM_PROMPT, userPrompt);
        } else {
            if (openAIClient == null) {
                throw new IllegalStateException("OpenAI client not configured");
            }
            aiOutput = openAIClient.generateQuestChapter(FtbQuestPrompts.SYSTEM_PROMPT + "\n\n" + userPrompt);
        }

        return extractSnbt(aiOutput);
    }

    public String generateModFocusedChapter(String modId, String theme, int questCount,
                                             String modItems, String additionalInstructions) {
        String userPrompt = FtbQuestPrompts.buildModFocusedPrompt(modId, theme, questCount, modItems, additionalInstructions);
        String aiOutput;

        if (activeProvider == AiProvider.OLLAMA) {
            aiOutput = ollamaClient.generate(FtbQuestPrompts.SYSTEM_PROMPT, userPrompt);
        } else {
            if (openAIClient == null) {
                throw new IllegalStateException("OpenAI client not configured");
            }
            aiOutput = openAIClient.generateQuestChapter(FtbQuestPrompts.SYSTEM_PROMPT + "\n\n" + userPrompt);
        }

        return extractSnbt(aiOutput);
    }

    @Deprecated(forRemoval = true)
    public String generateQuestChapter(QuestDesignSpec designSpec, ModIntent modIntent) {
        return generateChapterDraft(designSpec, modIntent);
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
