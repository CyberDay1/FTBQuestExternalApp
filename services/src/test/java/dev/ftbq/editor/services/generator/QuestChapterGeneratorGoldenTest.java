package dev.ftbq.editor.services.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ftbq.editor.domain.BackgroundRef;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.ChapterGroup;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.ItemTask;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.domain.Reward;
import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.io.snbt.SnbtQuestMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuestChapterGeneratorGoldenTest {

    @TempDir
    Path tempDir;

    private final SnbtQuestMapper mapper = new SnbtQuestMapper();

    @Test
    void generatorOutputMatchesGoldenRoundTrip() throws IOException {
        QuestFile base = baseQuestFile();
        QuestDesignSpec spec = new QuestDesignSpec(
                "Steam Power",
                List.of("intro", "midgame"),
                List.of("gate-by-pressure"),
                List.of("automation"),
                java.util.Set.of("minecraft:dragon_egg"),
                List.of("item"),
                2,
                20);
        ModIntent intent = new ModIntent("Create", List.of("steam"), "Introduce boilers", List.of());

        String modelResponse = readResource("golden/model_response.snbt");
        RecordingModelProvider provider = new RecordingModelProvider(modelResponse);

        QuestChapterGenerator generator = new QuestChapterGenerator(provider);
        Path draftsDir = tempDir.resolve("drafts");

        GenerationResult result = generator.generate(base, spec, intent, List.of(), draftsDir);

        QuestFile normalizedFile = QuestFile.builder()
                .id(base.id())
                .title(base.title())
                .chapterGroups(List.of())
                .chapters(result.chapters())
                .lootTables(List.of())
                .build();

        String normalizedSnbt = mapper.toSnbt(normalizedFile);
        String goldenSnbt = readResource("golden/generator_normalized.snbt");

        assertEquals(goldenSnbt, normalizedSnbt, () -> diffMessage(goldenSnbt, normalizedSnbt));

        QuestFile roundTripped = mapper.fromSnbt(normalizedSnbt);
        String secondPass = mapper.toSnbt(roundTripped);
        assertEquals(normalizedSnbt, secondPass, "Normalized SNBT should round-trip without diffs");

        try (var stream = Files.list(draftsDir)) {
            assertTrue(stream.findAny().isPresent(), "Expected generator to persist a draft file");
        }
    }

    private QuestFile baseQuestFile() {
        ChapterGroup group = ChapterGroup.builder()
                .id("core")
                .title("Core")
                .icon(new IconRef("minecraft:book"))
                .visibility(Visibility.VISIBLE)
                .chapterIds(new ArrayList<>(List.of("existing")))
                .build();

        Quest existingQuest = Quest.builder()
                .id("welcome")
                .title("Welcome")
                .description("Start here")
                .icon(new IconRef("minecraft:paper"))
                .visibility(Visibility.VISIBLE)
                .tasks(List.of(new ItemTask(new ItemRef("minecraft:stick", 1), true)))
                .rewards(List.of(Reward.xpLevels(3)))
                .dependencies(List.of())
                .build();

        Chapter existingChapter = Chapter.builder()
                .id("existing")
                .title("Existing Chapter")
                .icon(new IconRef("minecraft:book"))
                .background(new BackgroundRef("minecraft:textures/gui/advancements/backgrounds/stone.png"))
                .visibility(Visibility.VISIBLE)
                .quests(List.of(existingQuest))
                .build();

        return QuestFile.builder()
                .id("example_pack")
                .title("Example Pack")
                .chapterGroups(List.of(group))
                .chapters(List.of(existingChapter))
                .lootTables(List.of())
                .build();
    }

    private String readResource(String name) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("Missing golden resource: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String diffMessage(String expected, String actual) {
        String[] expectedLines = expected.split("\n");
        String[] actualLines = actual.split("\n");
        StringBuilder builder = new StringBuilder("Normalized SNBT differed from golden file:\n");
        int max = Math.max(expectedLines.length, actualLines.length);
        for (int i = 0; i < max; i++) {
            String left = i < expectedLines.length ? expectedLines[i] : "<missing>";
            String right = i < actualLines.length ? actualLines[i] : "<missing>";
            if (!left.equals(right)) {
                builder.append("- expected: ").append(left).append('\n');
                builder.append("+ actual  : ").append(right).append('\n');
            }
        }
        return builder.toString();
    }

    private static final class RecordingModelProvider implements AiModelProvider {
        private final String response;

        private RecordingModelProvider(String response) {
            this.response = response;
        }

        @Override
        public ModelResponse generate(ModelPrompt prompt) {
            return new ModelResponse(response, Map.of());
        }
    }
}
