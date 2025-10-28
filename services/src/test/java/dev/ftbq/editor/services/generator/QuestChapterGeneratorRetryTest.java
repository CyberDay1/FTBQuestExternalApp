package dev.ftbq.editor.services.generator;

import dev.ftbq.editor.domain.BackgroundRef;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.ItemTask;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.domain.Reward;
import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.importer.snbt.parser.SnbtParseException;
import dev.ftbq.editor.io.snbt.SnbtQuestMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestChapterGeneratorRetryTest {

    @Test
    void retriesWithCorrectiveNudgeAfterParseFailure() throws IOException {
        QuestFile baseQuestFile = QuestFile.builder()
                .id("pack")
                .title("Test Pack")
                .build();

        QuestDesignSpec spec = new QuestDesignSpec(
                "magic",
                List.of("intro", "challenge"),
                List.of("requires mana"),
                List.of("progression"),
                Set.of(),
                List.of("item"),
                3,
                6);

        ModIntent intent = new ModIntent("example_mod", List.of(), "", List.of());

        QuestFile generatedFile = QuestFile.builder()
                .id("generated")
                .title("Generated")
                .addChapter(sampleChapter("gen_chapter", "Generated Chapter", "generated_quest"))
                .build();

        RecordingModelProvider modelProvider = new RecordingModelProvider(
                List.of(new ModelResponse("invalid", Map.of()),
                        new ModelResponse(new SnbtQuestMapper().toSnbt(generatedFile), Map.of())));

        StubSnbtQuestMapper mapper = new StubSnbtQuestMapper(generatedFile);
        GeneratedContentValidator validator = new GeneratedContentValidator();
        PromptAssembler assembler = new PromptAssembler();
        QuestChapterGenerator generator = new QuestChapterGenerator(modelProvider, mapper, assembler, validator);

        Path draftsDir = Files.createTempDirectory("drafts");
        GenerationResult result = generator.generate(baseQuestFile, spec, intent, Collections.emptyList(), draftsDir);

        assertEquals(2, modelProvider.requestedPrompts.size(), "Generator should perform a retry");
        ModelPrompt retryPrompt = modelProvider.requestedPrompts.get(1);
        assertTrue(retryPrompt.blocks().stream()
                .anyMatch(block -> "system-nudge".equals(block.metadata().get("block"))),
                "Retry prompt should include corrective nudge");

        long retryLogCount = result.logs().stream()
                .filter(entry -> entry.stage().equals("postprocess")
                        && entry.message().contains("retrying with corrective nudge"))
                .count();
        assertEquals(1, retryLogCount, "Should log retry cause once");
    }

    private Chapter sampleChapter(String id, String title, String questId) {
        Quest quest = Quest.builder()
                .id(questId)
                .title("Quest " + questId)
                .description("Auto-generated")
                .icon(new IconRef("minecraft:book"))
                .visibility(Visibility.VISIBLE)
                .addTask(new ItemTask("minecraft:apple", 1))
                .addReward(Reward.xpLevels(1))
                .dependencies(List.of(new Dependency("root", true)))
                .build();

        return Chapter.builder()
                .id(id)
                .title(title)
                .icon(new IconRef("minecraft:book"))
                .background(new BackgroundRef("minecraft:textures/gui/default.png"))
                .visibility(Visibility.VISIBLE)
                .addQuest(quest)
                .build();
    }

    private static final class RecordingModelProvider implements AiModelProvider {
        private final Iterator<ModelResponse> responses;
        private final List<ModelPrompt> requestedPrompts = new ArrayList<>();

        private RecordingModelProvider(List<ModelResponse> responses) {
            this.responses = responses.iterator();
        }

        @Override
        public ModelResponse generate(ModelPrompt prompt) {
            requestedPrompts.add(prompt);
            if (!responses.hasNext()) {
                throw new IllegalStateException("No more responses configured");
            }
            return responses.next();
        }
    }

    private static final class StubSnbtQuestMapper extends SnbtQuestMapper {
        private final QuestFile success;
        private final AtomicInteger invalidCalls = new AtomicInteger();

        private StubSnbtQuestMapper(QuestFile success) {
            this.success = success;
        }

        @Override
        public QuestFile fromSnbt(String snbtText) {
            if (snbtText.contains("invalid")) {
                if (invalidCalls.getAndIncrement() < 2) {
                    throw new SnbtParseException("unterminated object");
                }
            }
            return success;
        }
    }
}
