package dev.ftbq.editor.services.generator;

import dev.ftbq.editor.domain.ChapterGroup;
import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.services.mods.ModRegistryService;
import dev.ftbq.editor.services.mods.RegisteredMod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Snapshot of existing quest content and user provided constraints.
 */
public final class GenerationContext {
    private final QuestFile questFile;
    private final QuestDesignSpec designSpec;
    private final ModIntent modIntent;
    private final List<ExampleChapterConstraint> examples;
    private final Map<String, Set<String>> progressionMap;
    private final List<RegisteredMod> selectedMods;

    public GenerationContext(QuestFile questFile,
                             QuestDesignSpec designSpec,
                             ModIntent modIntent,
                             List<ExampleChapterConstraint> examples,
                             Map<String, Set<String>> progressionMap,
                             List<RegisteredMod> selectedMods) {
        this.questFile = Objects.requireNonNull(questFile, "questFile");
        this.designSpec = Objects.requireNonNull(designSpec, "designSpec");
        this.modIntent = Objects.requireNonNull(modIntent, "modIntent");
        this.examples = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(examples, "examples")));
        this.progressionMap = Collections.unmodifiableMap(Objects.requireNonNull(progressionMap, "progressionMap"));
        Objects.requireNonNull(selectedMods, "selectedMods");
        if (selectedMods.size() > ModRegistryService.MAX_SELECTION) {
            throw new IllegalArgumentException("A maximum of "
                    + ModRegistryService.MAX_SELECTION
                    + " mods may be supplied. Received " + selectedMods.size());
        }
        this.selectedMods = Collections.unmodifiableList(new ArrayList<>(selectedMods));
    }

    public QuestFile questFile() {
        return questFile;
    }

    public QuestDesignSpec designSpec() {
        return designSpec;
    }

    public ModIntent modIntent() {
        return modIntent;
    }

    public List<ExampleChapterConstraint> examples() {
        return examples;
    }

    public Map<String, Set<String>> progressionMap() {
        return progressionMap;
    }

    public List<RegisteredMod> selectedMods() {
        return selectedMods;
    }

    public List<ChapterGroup> chapterGroups() {
        return questFile.chapterGroups();
    }

    public List<LootTable> lootTables() {
        return questFile.lootTables();
    }
}
