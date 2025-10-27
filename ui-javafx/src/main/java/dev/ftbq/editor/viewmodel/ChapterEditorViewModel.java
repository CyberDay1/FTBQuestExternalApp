package dev.ftbq.editor.viewmodel;

import dev.ftbq.editor.domain.BackgroundRef;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.Visibility;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Objects;

/**
 * View model that exposes the currently selected quest chapter for the editor UI.
 */
public class ChapterEditorViewModel {
    private final ObjectProperty<Chapter> chapter = new SimpleObjectProperty<>();
    private final ObservableList<Chapter> chapters = FXCollections.observableArrayList();

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
        return chapters;
    }

    public void loadChapter(Chapter chapter) {
        setChapter(Objects.requireNonNull(chapter, "chapter"));
    }

    public void loadSampleChapters() {
        Chapter gettingStarted = createGettingStartedChapter();
        Chapter exploration = createExplorationChapter();
        Chapter technology = createTechnologyChapter();

        chapters.setAll(gettingStarted, exploration, technology);
        setChapter(gettingStarted);
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
