package dev.ftbq.editor.view.graph;

import dev.ftbq.editor.domain.BackgroundRef;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.Quest;
import javafx.geometry.Point2D;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GraphCanvasTest {

    @Test
    void graphModelBuildsNodesAndEdges() {
        Chapter chapter = sampleChapter();
        Map<String, GraphCanvas.ValidationLevel> validations = new HashMap<>();
        validations.put("quest_a", GraphCanvas.ValidationLevel.OK);
        validations.put("quest_b", GraphCanvas.ValidationLevel.ERROR);
        validations.put("quest_c", GraphCanvas.ValidationLevel.WARNING);

        QuestGraphModel model = QuestGraphModel.fromChapter(chapter, validations);

        assertEquals(3, model.getNodes().size(), "Expected three quest nodes");
        assertEquals(2, model.getEdges().size(), "Expected two edges");

        QuestGraphModel.Node nodeB = model.findNode("quest_b").orElseThrow();
        Point2D original = nodeB.getPosition();
        model.moveNode("quest_b", original.getX() + 40, original.getY() + 30);
        QuestGraphModel.Node moved = model.findNode("quest_b").orElseThrow();
        assertEquals(original.getX() + 40, moved.getPosition().getX(), 0.0001);
        assertEquals(original.getY() + 30, moved.getPosition().getY(), 0.0001);

        QuestGraphModel.Edge incoming = model.getEdges().stream()
                .filter(edge -> edge.getTarget().getQuest().id().equals("quest_b"))
                .findFirst()
                .orElseThrow();
        assertEquals("quest_a", incoming.getSource().getQuest().id());
        assertTrue(incoming.isRequired());

        model.updateValidation("quest_c", GraphCanvas.ValidationLevel.OK);
        assertEquals(GraphCanvas.ValidationLevel.OK,
                model.findNode("quest_c").orElseThrow().getValidationLevel());
    }

    private Chapter sampleChapter() {
        Quest questA = Quest.builder()
                .id("quest_a")
                .title("Quest A")
                .icon(new IconRef("minecraft:apple"))
                .build();

        Quest questB = Quest.builder()
                .id("quest_b")
                .title("Quest B")
                .icon(new IconRef("minecraft:bread"))
                .addDependency(new Dependency("quest_a", true))
                .build();

        Quest questC = Quest.builder()
                .id("quest_c")
                .title("Quest C")
                .icon(new IconRef("minecraft:carrot"))
                .addDependency(new Dependency("quest_b", false))
                .build();

        Chapter.Builder builder = Chapter.builder()
                .id("chapter_demo")
                .title("Demo Chapter")
                .background(new BackgroundRef("demo:texture"));
        builder.addQuest(questA);
        builder.addQuest(questB);
        builder.addQuest(questC);
        return builder.build();
    }
}
