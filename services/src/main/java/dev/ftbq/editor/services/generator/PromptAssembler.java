package dev.ftbq.editor.services.generator;

import dev.ftbq.editor.domain.ChapterGroup;
import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.domain.QuestFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Builds prompt blocks that encode the pack context and authoring guidelines.
 */
public final class PromptAssembler {

    public ModelPrompt buildPrompt(GenerationContext context) {
        List<PromptBlock> blocks = new ArrayList<>();
        blocks.add(PromptBlock.of(PromptRole.SYSTEM, buildSystemInstructions(context.designSpec())));
        blocks.add(PromptBlock.of(PromptRole.USER, buildUserContext(context)));
        for (ExampleChapterConstraint example : context.examples()) {
            String content = "# Example chapters from %s\n%s".formatted(example.source().getFileName(), example.snbt());
            blocks.add(new PromptBlock(PromptRole.EXAMPLE, content, Map.of("source", example.source().toString())));
        }
        return new ModelPrompt(blocks);
    }

    private String buildSystemInstructions(QuestDesignSpec spec) {
        String allowedTasks = String.join(", ", spec.allowedTasks());
        String difficulty = String.join(" → ", spec.difficultyCurve());
        String gatingRules = String.join(", ", spec.gatingRules());
        String progressionAxes = String.join(", ", spec.progressionAxes());
        String blacklist = spec.itemBlacklist().isEmpty()
                ? "None"
                : String.join(", ", spec.itemBlacklist());
        return "You are an expert FTB Quests chapter designer. Produce strictly valid SNBT describing chapters only. "
                + "Use canonical ordering: id, title, icon, background, visibility, quests. "
                + "Each quest must include id, title, description, icon, visibility, tasks, rewards, dependencies. "
                + "Never include commentary or markdown outside of SNBT blocks.\n"
                + "Theme: " + spec.theme() + '\n'
                + "Difficulty curve: " + difficulty + '\n'
                + "Gating rules: " + gatingRules + '\n'
                + "Progression axes: " + progressionAxes + '\n'
                + "Allowed tasks: " + allowedTasks + '\n'
                + "Item blacklist: " + blacklist + '\n'
                + "Target chapter length: " + spec.chapterLength() + " quests\n"
                + "Reward budget: " + spec.rewardBudget();
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
        builder.append('\n').append("Keep rewards within the budget and map new quests into existing groups."
                + " Use IDs that avoid collisions.");
        return builder.toString();
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
