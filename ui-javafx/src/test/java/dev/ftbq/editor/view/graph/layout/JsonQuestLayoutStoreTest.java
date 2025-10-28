package dev.ftbq.editor.view.graph.layout;

import javafx.geometry.Point2D;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonQuestLayoutStoreTest {

    private Path workspace;
    private JsonQuestLayoutStore store;

    @BeforeEach
    void setUp() throws IOException {
        workspace = Files.createTempDirectory("quest-layout-store-test");
        store = new JsonQuestLayoutStore(workspace);
    }

    @AfterEach
    void tearDown() throws IOException {
        store.flush();
        Files.walk(workspace)
                .sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
    }

    @Test
    void persistsAndReloadsPositions() {
        store.putNodePos("chapter_one", "quest_a", 12.5, -4.25);
        store.flush();

        JsonQuestLayoutStore reloaded = new JsonQuestLayoutStore(workspace);
        Optional<Point2D> restored = reloaded.getNodePos("chapter_one", "quest_a");

        assertTrue(restored.isPresent(), "Position should be restored after reload");
        assertEquals(12.5, restored.get().getX(), 0.0001);
        assertEquals(-4.25, restored.get().getY(), 0.0001);
    }

    @Test
    void removesQuestEntries() {
        store.putNodePos("chapter_two", "quest_b", 10.0, 15.0);
        store.flush();

        store.removeQuest("chapter_two", "quest_b");
        store.flush();

        JsonQuestLayoutStore reloaded = new JsonQuestLayoutStore(workspace);
        assertTrue(reloaded.getNodePos("chapter_two", "quest_b").isEmpty(),
                "Removed quest position should not be restored");
    }

    @Test
    void removesChapterEntries() {
        store.putNodePos("chapter_three", "quest_c", 1.0, 2.0);
        store.putNodePos("chapter_three", "quest_d", 3.0, 4.0);
        store.flush();

        store.removeChapter("chapter_three");
        store.flush();

        JsonQuestLayoutStore reloaded = new JsonQuestLayoutStore(workspace);
        assertTrue(reloaded.getNodePos("chapter_three", "quest_c").isEmpty(),
                "Chapter removal should clear quest positions");
        assertTrue(reloaded.getNodePos("chapter_three", "quest_d").isEmpty(),
                "All quests for removed chapter should be gone");
    }
}
