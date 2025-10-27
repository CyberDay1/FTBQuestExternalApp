package dev.ftbq.editor.validation;

import dev.ftbq.editor.domain.BackgroundRef;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyCycleValidatorTest {

    private final DependencyCycleValidator validator = new DependencyCycleValidator();

    @Test
    void detectsSimpleCycle() {
        Quest questA = Quest.builder()
                .id("quest_a")
                .title("Quest A")
                .description("A")
                .icon(new IconRef("minecraft:apple"))
                .addDependency(new Dependency("quest_b", true))
                .build();

        Quest questB = Quest.builder()
                .id("quest_b")
                .title("Quest B")
                .description("B")
                .icon(new IconRef("minecraft:bread"))
                .addDependency(new Dependency("quest_a", true))
                .build();

        Chapter chapter = Chapter.builder()
                .id("chapter_one")
                .title("Chapter One")
                .icon(new IconRef("minecraft:book"))
                .background(new BackgroundRef("minecraft:textures/gui/default.png"))
                .quests(List.of(questA, questB))
                .build();

        QuestFile file = QuestFile.builder()
                .id("demo")
                .title("Demo")
                .chapters(List.of(chapter))
                .build();

        List<ValidationIssue> issues = validator.validate(file, resolver());

        assertEquals(1, issues.size());
        ValidationIssue issue = issues.get(0);
        assertEquals("ERROR", issue.severity());
        assertEquals("chapter/chapter_one/quests/quest_a", issue.path());
        assertTrue(issue.message().contains("quest_a"));
        assertTrue(issue.message().contains("quest_b"));
    }

    @Test
    void ignoresDependenciesOutsideChapter() {
        Quest questA = Quest.builder()
                .id("quest_a")
                .title("Quest A")
                .description("A")
                .icon(new IconRef("minecraft:apple"))
                .addDependency(new Dependency("quest_external", true))
                .build();

        Quest questB = Quest.builder()
                .id("quest_b")
                .title("Quest B")
                .description("B")
                .icon(new IconRef("minecraft:bread"))
                .addDependency(new Dependency("quest_a", true))
                .build();

        Chapter chapter = Chapter.builder()
                .id("chapter_one")
                .title("Chapter One")
                .icon(new IconRef("minecraft:book"))
                .background(new BackgroundRef("minecraft:textures/gui/default.png"))
                .quests(List.of(questA, questB))
                .build();

        QuestFile file = QuestFile.builder()
                .id("demo")
                .title("Demo")
                .chapters(List.of(chapter))
                .build();

        List<ValidationIssue> issues = validator.validate(file, resolver());

        assertTrue(issues.isEmpty());
    }

    private Validator.ItemResolver resolver() {
        return new Validator.ItemResolver() { };
    }
}
