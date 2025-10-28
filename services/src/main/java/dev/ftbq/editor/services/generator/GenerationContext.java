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
    private final ModSelection modSelection;
    private final QuestLimits questLimits;
    private final RewardConfiguration rewardConfiguration;

    public GenerationContext(QuestFile questFile,
                             QuestDesignSpec designSpec,
                             ModIntent modIntent,
                             List<ExampleChapterConstraint> examples,
                             Map<String, Set<String>> progressionMap,
                             List<RegisteredMod> selectedMods) {
        this(questFile,
                designSpec,
                modIntent,
                examples,
                progressionMap,
                new ModSelection(selectedMods, ModRegistryService.MAX_SELECTION),
                new QuestLimits(Math.min(designSpec.chapterLength(), QuestLimits.MAX_AI_QUESTS),
                        QuestLimits.MAX_AI_QUESTS),
                RewardConfiguration.allowAll());
    }

    public GenerationContext(QuestFile questFile,
                             QuestDesignSpec designSpec,
                             ModIntent modIntent,
                             List<ExampleChapterConstraint> examples,
                             Map<String, Set<String>> progressionMap,
                             ModSelection modSelection,
                             QuestLimits questLimits,
                             RewardConfiguration rewardConfiguration) {
        this.questFile = Objects.requireNonNull(questFile, "questFile");
        this.designSpec = Objects.requireNonNull(designSpec, "designSpec");
        this.modIntent = Objects.requireNonNull(modIntent, "modIntent");
        this.examples = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(examples, "examples")));
        this.progressionMap = Collections.unmodifiableMap(Objects.requireNonNull(progressionMap, "progressionMap"));
        this.modSelection = Objects.requireNonNull(modSelection, "modSelection");
        if (modSelection.limit() > ModRegistryService.MAX_SELECTION) {
            throw new IllegalArgumentException("modSelection limit exceeds registry maximum");
        }
        this.questLimits = Objects.requireNonNull(questLimits, "questLimits");
        this.rewardConfiguration = Objects.requireNonNull(rewardConfiguration, "rewardConfiguration");
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
        return modSelection.mods();
    }

    public ModSelection modSelection() {
        return modSelection;
    }

    public QuestLimits questLimits() {
        return questLimits;
    }

    public RewardConfiguration rewardConfiguration() {
        return rewardConfiguration;
    }

    public List<ChapterGroup> chapterGroups() {
        return questFile.chapterGroups();
    }

    public List<LootTable> lootTables() {
        return questFile.lootTables();
    }
}
