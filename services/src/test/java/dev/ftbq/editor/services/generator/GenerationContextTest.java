package dev.ftbq.editor.services.generator;

import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.services.mods.ModRegistryService;
import dev.ftbq.editor.services.mods.RegisteredMod;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class GenerationContextTest {

    @Test
    void rejectsWhenSelectedModLimitExceeded() {
        QuestFile questFile = QuestFile.builder()
                .id("example")
                .title("Example")
                .chapters(List.of())
                .chapterGroups(List.of())
                .lootTables(List.of())
                .build();
        QuestDesignSpec spec = new QuestDesignSpec(
                "theme",
                List.of(),
                List.of(),
                List.of(),
                Set.of(),
                List.of(),
                1,
                1);
        ModIntent intent = new ModIntent("minecraft", List.of(), "", List.of());
        List<RegisteredMod> mods = List.of(new RegisteredMod("a", "A", "1", List.of("a:item"), "a.jar"));
        for (int i = 1; i < ModRegistryService.MAX_SELECTION + 1; i++) {
            mods = new java.util.ArrayList<>(mods);
            mods.add(new RegisteredMod("mod" + i, "Mod" + i, "1", List.of("mod:item" + i), "mod.jar"));
        }
        List<RegisteredMod> overLimit = mods;

        List<ExampleChapterConstraint> examples = List.of();
        Map<String, Set<String>> progression = Map.of();

        List<RegisteredMod> finalOverLimit = overLimit;
        assertThrows(IllegalArgumentException.class, () ->
                new GenerationContext(questFile, spec, intent, examples, progression, finalOverLimit));
    }
}
