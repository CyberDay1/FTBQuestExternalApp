package dev.ftbq.editor.viewmodel;

import dev.ftbq.editor.domain.BackgroundRef;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.services.UiServiceLocator;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * View model that exposes the currently selected quest chapter for the editor UI.
 */
public class ChapterEditorViewModel {
    private final ObjectProperty<Chapter> chapter = new SimpleObjectProperty<>();
    private final ObservableList<Chapter> chapters = FXCollections.observableArrayList();
    private final FilteredList<Chapter> filteredChapters = new FilteredList<>(chapters, ignored -> true);
    private final StringProperty chapterFilter = new SimpleStringProperty("");

    public ChapterEditorViewModel() {
        chapterFilter.addListener((obs, oldValue, newValue) -> applyFilter(newValue));
        applyFilter(chapterFilter.get());
    }

    public ObjectProperty<Chapter> chapterProperty() {
        return chapter;
    }

    public Chapter getChapter() {
        return chapter.get();
    }

    public void setChapter(Chapter chapter) {
        this.chapter.set(chapter);
    }

    public ObservableList<Chapter> getChapters() {
        return filteredChapters;
    }

    public void loadChapter(Chapter chapter) {
        setChapter(Objects.requireNonNull(chapter, "chapter"));
    }

    public StringProperty chapterFilterProperty() {
        return chapterFilter;
    }

    public void loadChaptersFromStore() {
        if (UiServiceLocator.storeDao == null) {
            return;
        }
        List<Chapter> loaded = UiServiceLocator.storeDao.loadChapters();
        setChapters(loaded);
    }

    public void setChapters(List<Chapter> newChapters) {
        Objects.requireNonNull(newChapters, "newChapters");
        String selectedId = Optional.ofNullable(getChapter()).map(Chapter::id).orElse(null);
        chapters.setAll(newChapters);
        Chapter selected = chapters.stream()
                .filter(chapter -> Objects.equals(chapter.id(), selectedId))
                .findFirst()
                .orElseGet(() -> chapters.isEmpty() ? null : chapters.get(0));
        setChapter(selected);
    }

    public void reorderChapter(Chapter chapter, int filteredTargetIndex) {
        Objects.requireNonNull(chapter, "chapter");
        if (filteredChapters.isEmpty()) {
            return;
        }
        int sourceIndex = chapters.indexOf(chapter);
        if (sourceIndex < 0) {
            return;
        }
        int targetIndex = filteredTargetIndex;
        if (targetIndex < 0) {
            targetIndex = 0;
        } else if (targetIndex >= filteredChapters.size()) {
            targetIndex = filteredChapters.size() - 1;
        }
        int destinationIndex = filteredChapters.getSourceIndex(targetIndex);
        if (destinationIndex < 0) {
            destinationIndex = chapters.size() - 1;
        }
        if (destinationIndex == sourceIndex) {
            return;
        }
        Chapter removed = chapters.remove(sourceIndex);
        if (destinationIndex > sourceIndex) {
            destinationIndex--;
        }
        chapters.add(destinationIndex, removed);
        if (UiServiceLocator.storeDao != null) {
            UiServiceLocator.storeDao.reorderChapter(chapter.id(), destinationIndex);
        }
    }

    public void moveQuestToChapter(String questId, Chapter targetChapter) {
        Objects.requireNonNull(questId, "questId");
        Objects.requireNonNull(targetChapter, "targetChapter");
        Chapter activeChapter = getChapter();
        if (activeChapter == null || Objects.equals(activeChapter.id(), targetChapter.id())) {
            return;
        }
        Quest quest = activeChapter.quests().stream()
                .filter(q -> questId.equals(q.id()))
                .findFirst()
                .orElse(null);
        if (quest == null) {
            return;
        }
        Chapter updatedSource = rebuildChapter(activeChapter, removeQuest(activeChapter, questId));
        Chapter resolvedTarget = findChapterById(targetChapter.id()).orElse(targetChapter);
        Chapter updatedTarget = rebuildChapter(resolvedTarget, appendQuest(resolvedTarget, quest));

        replaceChapter(activeChapter, updatedSource);
        replaceChapter(resolvedTarget, updatedTarget);

        if (Objects.equals(getChapter(), activeChapter)) {
            setChapter(updatedSource);
        }
        if (Objects.equals(getChapter(), resolvedTarget)) {
            setChapter(updatedTarget);
        }

        if (UiServiceLocator.storeDao != null) {
            UiServiceLocator.storeDao.moveQuestToChapter(questId, targetChapter.id());
        }
    }

    public void loadSampleChapters() {
        Chapter gettingStarted = createGettingStartedChapter();
        Chapter exploration = createExplorationChapter();
        Chapter technology = createTechnologyChapter();

        setChapters(List.of(gettingStarted, exploration, technology));
    }

    public void loadFromQuestFile(QuestFile questFile) {
        Objects.requireNonNull(questFile, "questFile");
        setChapters(questFile.chapters());
    }

    private void applyFilter(String filterText) {
        if (filterText == null || filterText.isBlank()) {
            filteredChapters.setPredicate(chapter -> true);
            return;
        }
        String normalized = filterText.toLowerCase(Locale.ROOT);
        filteredChapters.setPredicate(chapter ->
                chapter != null && chapter.title().toLowerCase(Locale.ROOT).contains(normalized));
    }

    private Optional<Chapter> findChapterById(String id) {
        return chapters.stream()
                .filter(chapter -> Objects.equals(chapter.id(), id))
                .findFirst();
    }

    private void replaceChapter(Chapter original, Chapter updated) {
        int index = chapters.indexOf(original);
        if (index >= 0) {
            chapters.set(index, updated);
        }
    }

    private List<Quest> removeQuest(Chapter chapter, String questId) {
        List<Quest> quests = new ArrayList<>(chapter.quests());
        quests.removeIf(quest -> questId.equals(quest.id()));
        return quests;
    }

    private List<Quest> appendQuest(Chapter chapter, Quest quest) {
        List<Quest> quests = new ArrayList<>(chapter.quests());
        quests.add(quest);
        return quests;
    }

    private Chapter rebuildChapter(Chapter original, List<Quest> quests) {
        return Chapter.builder()
                .id(original.id())
                .title(original.title())
                .icon(original.icon())
                .background(original.background())
                .visibility(original.visibility())
                .quests(quests)
                .build();
    }

    private Chapter createGettingStartedChapter() {
        Chapter.Builder builder = Chapter.builder()
                .id("chapter_getting_started")
                .title("Getting Started")
                .background(new BackgroundRef("minecraft:textures/gui/demo.png"));

        Quest gatherWood = Quest.builder()
                .id("gather_wood")
                .title("Gather Wood")
                .description("Collect 16 logs to start your journey.")
                .icon(new IconRef("minecraft:oak_log"))
                .visibility(Visibility.VISIBLE)
                .build();

        Quest craftTable = Quest.builder()
                .id("craft_table")
                .title("Craft a Table")
                .description("Turn logs into planks and craft a crafting table.")
                .icon(new IconRef("minecraft:crafting_table"))
                .addDependency(new Dependency("gather_wood", true))
                .visibility(Visibility.VISIBLE)
                .build();

        Quest makeTools = Quest.builder()
                .id("make_tools")
                .title("Make Tools")
                .description("Craft a set of wooden tools to get started.")
                .icon(new IconRef("minecraft:wooden_pickaxe"))
                .addDependency(new Dependency("craft_table", true))
                .visibility(Visibility.VISIBLE)
                .build();

        Quest explore = Quest.builder()
                .id("explore_world")
                .title("Explore the World")
                .description("Venture out and find a suitable place for your base.")
                .icon(new IconRef("minecraft:map"))
                .addDependency(new Dependency("make_tools", false))
                .visibility(Visibility.VISIBLE)
                .build();

        builder.addQuest(gatherWood);
        builder.addQuest(craftTable);
        builder.addQuest(makeTools);
        builder.addQuest(explore);

        return builder.build();
    }

    private Chapter createExplorationChapter() {
        Chapter.Builder builder = Chapter.builder()
                .id("chapter_exploration")
                .title("Exploration Adventures")
                .background(new BackgroundRef("minecraft:textures/gui/options_background.png"));

        Quest mapTheWorld = Quest.builder()
                .id("map_world")
                .title("Map the World")
                .description("Craft a map and chart the surrounding area.")
                .icon(new IconRef("minecraft:filled_map"))
                .visibility(Visibility.VISIBLE)
                .build();

        Quest findVillage = Quest.builder()
                .id("find_village")
                .title("Find a Village")
                .description("Locate a village and trade with the locals.")
                .icon(new IconRef("minecraft:emerald"))
                .addDependency(new Dependency("map_world", true))
                .visibility(Visibility.VISIBLE)
                .build();

        Quest netherPortal = Quest.builder()
                .id("build_portal")
                .title("Build a Nether Portal")
                .description("Construct a portal to explore the Nether.")
                .icon(new IconRef("minecraft:obsidian"))
                .addDependency(new Dependency("find_village", false))
                .visibility(Visibility.VISIBLE)
                .build();

        builder.addQuest(mapTheWorld);
        builder.addQuest(findVillage);
        builder.addQuest(netherPortal);

        return builder.build();
    }

    private Chapter createTechnologyChapter() {
        Chapter.Builder builder = Chapter.builder()
                .id("chapter_technology")
                .title("Technology Tree")
                .background(new BackgroundRef("minecraft:textures/gui/container/brewing_stand.png"));

        Quest gatherIron = Quest.builder()
                .id("gather_iron")
                .title("Gather Iron")
                .description("Collect iron ingots for advanced tools.")
                .icon(new IconRef("minecraft:iron_ingot"))
                .visibility(Visibility.VISIBLE)
                .build();

        Quest craftAnvil = Quest.builder()
                .id("craft_anvil")
                .title("Craft an Anvil")
                .description("Use iron blocks to craft an anvil.")
                .icon(new IconRef("minecraft:anvil"))
                .addDependency(new Dependency("gather_iron", true))
                .visibility(Visibility.VISIBLE)
                .build();

        Quest enchantGear = Quest.builder()
                .id("enchant_gear")
                .title("Enchant Gear")
                .description("Set up an enchanting area and enchant your gear.")
                .icon(new IconRef("minecraft:enchanting_table"))
                .addDependency(new Dependency("craft_anvil", false))
                .visibility(Visibility.VISIBLE)
                .build();

        builder.addQuest(gatherIron);
        builder.addQuest(craftAnvil);
        builder.addQuest(enchantGear);

        return builder.build();
    }
}


