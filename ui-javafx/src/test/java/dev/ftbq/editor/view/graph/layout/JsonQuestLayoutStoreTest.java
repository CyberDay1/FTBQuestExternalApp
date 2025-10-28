package dev.ftbq.editor.view.graph.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JsonQuestLayoutStoreTest {

    @TempDir
    Path workspace;

    private JsonQuestLayoutStore store;

    @AfterEach
    void tearDown() throws Exception {
        if (store != null) {
            store.flush();
            shutdownExecutor(store);
        }
    }

    @Test
    void persistsAndReloadsQuestPositions() throws Exception {
        store = new JsonQuestLayoutStore(workspace, Duration.ZERO);

        store.putNodePos("chapter1", "questA", 32.5, 48.0);
        store.putNodePos("chapter1", "questB", 64.0, 12.0);
        store.flush();

        Optional<javafx.geometry.Point2D> questAPosition = store.getNodePos("chapter1", "questA");
        assertTrue(questAPosition.isPresent(), "Stored position should be available immediately");
        assertEquals(32.5, questAPosition.get().getX());
        assertEquals(48.0, questAPosition.get().getY());

        // Reload from disk to ensure persistence works
        shutdownExecutor(store);
        store = new JsonQuestLayoutStore(workspace, Duration.ZERO);
        Optional<javafx.geometry.Point2D> reloaded = store.getNodePos("chapter1", "questB");
        assertTrue(reloaded.isPresent(), "Reloaded position should be restored from disk");
        assertEquals(64.0, reloaded.get().getX());
        assertEquals(12.0, reloaded.get().getY());
    }

    private void shutdownExecutor(JsonQuestLayoutStore layoutStore) throws Exception {
        Field executorField = JsonQuestLayoutStore.class.getDeclaredField("executor");
        executorField.setAccessible(true);
        ScheduledExecutorService executor = (ScheduledExecutorService) executorField.get(layoutStore);
        executor.shutdownNow();
    }
}
