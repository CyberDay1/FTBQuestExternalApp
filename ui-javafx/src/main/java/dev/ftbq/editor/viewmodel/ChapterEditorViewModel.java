package dev.ftbq.editor.viewmodel;

import dev.ftbq.editor.domain.BackgroundRef;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.Visibility;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.util.ArrayList;
import java.util.Objects;

/**
 * View model that exposes the currently selected quest chapter for the editor UI.
 */
public class ChapterEditorViewModel {
    private final ObjectProperty<Chapter> chapter = new SimpleObjectProperty<>();

    public ObjectProperty<Chapter> chapterProperty() {
        return chapter;
    }

    public Chapter getChapter() {
        return chapter.get();
    }

    public void setChapter(Chapter chapter) {
        this.chapter.set(chapter);
    }

    public void loadChapter(Chapter chapter) {
        setChapter(Objects.requireNonNull(chapter, "chapter"));
    }

    public void loadSampleChapter() {
        Chapter.Builder builder = Chapter.builder()
                .id("chapter_demo")
                .title("Demo Chapter")
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

        setChapter(builder.build());
    }

    public void updateQuest(Quest updatedQuest) {
        Objects.requireNonNull(updatedQuest, "updatedQuest");
        Chapter currentChapter = getChapter();
        if (currentChapter == null) {
            return;
        }
        java.util.List<Quest> quests = new ArrayList<>(currentChapter.quests());
        boolean replaced = false;
        for (int i = 0; i < quests.size(); i++) {
            Quest quest = quests.get(i);
            if (quest.id().equals(updatedQuest.id())) {
                quests.set(i, updatedQuest);
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            return;
        }
        Chapter.Builder builder = Chapter.builder()
                .id(currentChapter.id())
                .title(currentChapter.title())
                .icon(currentChapter.icon())
                .background(currentChapter.background())
                .visibility(currentChapter.visibility())
                .quests(quests);
        setChapter(builder.build());
    }
}
