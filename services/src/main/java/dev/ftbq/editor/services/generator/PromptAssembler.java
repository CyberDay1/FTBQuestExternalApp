package dev.ftbq.editor.services.generator;

import dev.ftbq.editor.domain.ChapterGroup;
import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.services.mods.RegisteredMod;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Builds prompt blocks that encode the pack context and authoring guidelines.
 */
public final class PromptAssembler {

    private static final String SYSTEM_MESSAGE =
            "You produce FTB Quests chapters in SNBT. Follow exact key names and structures. Do not emit JSON.";
    private static final int SOFT_TASK_MIN = 1;
    private static final int SOFT_TASK_MAX = 4;
    private static final int MAX_DEPENDENCY_IN_DEGREE = 3;

    public ModelPrompt buildPrompt(GenerationContext context) {
        return buildPrompt(context, null);
    }

    public ModelPrompt buildPromptWithNudge(GenerationContext context, String correctiveNudge) {
        Objects.requireNonNull(correctiveNudge, "correctiveNudge");
        if (correctiveNudge.isBlank()) {
            throw new IllegalArgumentException("correctiveNudge cannot be blank");
        }
        return buildPrompt(context, correctiveNudge.trim());
    }

    private ModelPrompt buildPrompt(GenerationContext context, String correctiveNudge) {
        List<PromptBlock> blocks = new ArrayList<>();
        blocks.add(new PromptBlock(PromptRole.SYSTEM, SYSTEM_MESSAGE, Map.of("block", "system")));
        blocks.add(new PromptBlock(PromptRole.SYSTEM,
                buildStyleGuide(context.designSpec()),
                Map.of("block", "style-guide")));
        blocks.add(new PromptBlock(PromptRole.SYSTEM,
                buildConstraints(context),
                Map.of("block", "constraints")));
        blocks.add(new PromptBlock(PromptRole.SYSTEM,
                buildOutputContract(),
                Map.of("block", "output-contract")));
        blocks.add(new PromptBlock(PromptRole.USER,
                buildUserContext(context),
                Map.of("block", "pack-context")));
        for (ExampleChapterConstraint example : context.examples()) {
            blocks.add(new PromptBlock(PromptRole.EXAMPLE,
                    example.snbt(),
                    Map.of(
                            "block", "example",
                            "source", example.source().toString())));
        }
        if (correctiveNudge != null) {
            blocks.add(new PromptBlock(PromptRole.SYSTEM,
                    buildCorrectiveNudge(correctiveNudge),
                    Map.of("block", "system-nudge")));
        }
        return new ModelPrompt(blocks);
    }

    private String buildStyleGuide(QuestDesignSpec spec) {
        String allowedTasks = String.join(", ", spec.allowedTasks());
        String progressionAxes = String.join(" · ", spec.progressionAxes());
        String blacklist = spec.itemBlacklist().isEmpty()
                ? "None"
                : String.join(", ", spec.itemBlacklist());
        return "Style guide:\n"
                + "- Maintain chapter key order: id, title, icon, background, visibility, quests.\n"
                + "- Maintain quest key order: id, title, description, icon, visibility, tasks, rewards, dependencies.\n"
                + "- Use lower_snake_case ids themed around " + spec.theme() + ".\n"
                + "- Prefer mod-relevant icons; fall back to minecraft:book only when no better fit exists.\n"
                + "- Allowed task types: " + allowedTasks + ".\n"
                + "- List dependencies in progression order and group optional ones last.\n"
                + "- Titles should use localization keys when referencing reusable text (e.g., title:{translate:\"pack.chapter.key\"}).\n"
                + "- Avoid blacklisted items: " + blacklist + ".\n"
                + "- Highlight progression axes: " + progressionAxes + ".";
    }

    private String buildConstraints(GenerationContext context) {
        QuestDesignSpec spec = context.designSpec();
        String difficulty = String.join(" → ", spec.difficultyCurve());
        String gatingRules = String.join(", ", spec.gatingRules());
        String lootTables = context.lootTables().isEmpty()
                ? "no existing reward tables"
                : String.join(", ", context.lootTables().stream().map(LootTable::id).toList());
        return "Constraints:\n"
                + "- Hard cap " + spec.chapterLength() + " quests in the chapter.\n"
                + "- Soft target " + SOFT_TASK_MIN + "-" + SOFT_TASK_MAX + " tasks per quest; avoid exceeding " + SOFT_TASK_MAX + ".\n"
                + "- Difficulty progression: " + difficulty + ".\n"
                + "- Apply gating rules: " + gatingRules + ".\n"
                + "- Keep dependency graph acyclic; no quest may have more than " + MAX_DEPENDENCY_IN_DEGREE + " parents.\n"
                + "- Align total rewards with budget " + spec.rewardBudget() + " and available tables (" + lootTables + ").\n"
                + "- Map new quests into existing chapter groups without introducing dependency loops.";
    }

    private String buildOutputContract() {
        return "Output contract:\n"
                + "- Emit a single chapter SNBT object per response.\n"
                + "- SNBT only; no commentary, markdown, or JSON.\n"
                + "- Use UTF-8 characters exclusively.\n"
                + "- Do not include trailing explanations.";
    }

    private String buildUserContext(GenerationContext context) {
        QuestFile questFile = context.questFile();
        StringBuilder builder = new StringBuilder();
        builder.append("Existing pack: ").append(questFile.id()).append(" - ").append(questFile.title()).append('\n');
        builder.append("Mod intent: ").append(context.modIntent().modId()).append('\n');
        if (!context.modIntent().features().isEmpty()) {
            builder.append("Target features: ").append(String.join(", ", context.modIntent().features())).append('\n');
        }
        if (!context.modIntent().progressionNotes().isBlank()) {
            builder.append("Progression notes: ").append(context.modIntent().progressionNotes()).append('\n');
        }
        if (!context.modIntent().exampleReferences().isEmpty()) {
            builder.append("Reference builds: ")
                    .append(String.join(", ", context.modIntent().exampleReferences()))
                    .append('\n');
        }
        if (!context.selectedMods().isEmpty()) {
            builder.append('\n').append("Selected mods:\n");
            for (RegisteredMod mod : context.selectedMods()) {
                builder.append(" - ")
                        .append(mod.displayName())
                        .append(" [")
                        .append(mod.modId())
                        .append(']');
                if (mod.version() != null && !mod.version().isBlank()) {
                    builder.append(" v").append(mod.version());
                }
                if (!mod.itemIds().isEmpty()) {
                    builder.append(" items: ")
                            .append(summarizeItems(mod.itemIds()));
                }
                builder.append('\n');
            }
        }
        builder.append('\n').append("Chapter groups:\n");
        for (ChapterGroup group : context.chapterGroups()) {
            builder.append(" - ").append(group.id()).append(" (" + group.title() + ") → ")
                    .append(String.join(", ", group.chapterIds()))
                    .append(" [").append(group.visibility().name().toLowerCase(Locale.ROOT)).append("]\n");
        }
        builder.append('\n').append("Reward tables:\n");
        if (context.lootTables().isEmpty()) {
            builder.append(" - None defined\n");
        } else {
            for (LootTable table : context.lootTables()) {
                builder.append(" - ").append(table.id()).append(" (pools=").append(table.pools().size()).append(")\n");
            }
        }
        builder.append('\n').append("Progression map:\n");
        Map<String, Set<String>> progressionMap = context.progressionMap();
        for (Map.Entry<String, Set<String>> entry : progressionMap.entrySet()) {
            builder.append(" - ").append(entry.getKey()).append(" depends on ");
            if (entry.getValue().isEmpty()) {
                builder.append("nothing");
            } else {
                builder.append(String.join(", ", entry.getValue()));
            }
            builder.append('\n');
        }
        builder.append('\n')
                .append("Keep rewards within the budget and map new quests into existing groups.")
                .append(' ')
                .append("Use IDs that avoid collisions.");
        return builder.toString();
    }

    private String buildCorrectiveNudge(String correctiveNudge) {
        return "Corrective action: " + correctiveNudge
                + "\nRegenerate strictly valid SNBT adhering to the style, constraints, and output contract.";
    }

    private String summarizeItems(List<String> itemIds) {
        if (itemIds.isEmpty()) {
            return "";
        }
        final int limit = 15;
        if (itemIds.size() <= limit) {
            return String.join(", ", itemIds);
        }
        String joined = String.join(", ", itemIds.subList(0, limit));
        int remaining = itemIds.size() - limit;
        return joined + " … +" + remaining + " more";
    }

    public String describe(ModelPrompt prompt) {
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (PromptBlock block : prompt.blocks()) {
            builder.append(index++)
                    .append('.')
                    .append(' ')
                    .append(block.role().name().toLowerCase(Locale.ROOT))
                    .append(':');
            if (block.metadata().isEmpty()) {
                builder.append(' ');
            } else {
                StringJoiner joiner = new StringJoiner(", ");
                block.metadata().forEach((key, value) -> joiner.add(key + '=' + value));
                builder.append(' ').append('[').append(joiner).append(']').append(' ');
            }
            String snippet = block.content().length() > 160
                    ? block.content().substring(0, 157) + "..."
                    : block.content();
            builder.append(snippet.replace('\n', ' ')).append('\n');
        }
        return builder.toString();
    }
}
