package dev.ftbq.editor.ui.graph;

import javafx.application.Platform;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class QuestNodeViewTest {

    @BeforeAll
    static void initToolkit() throws InterruptedException {
        System.setProperty("javafx.platform", "Monocle");
        System.setProperty("monocle.platform", "Headless");
        System.setProperty("glass.platform", "Monocle");
        System.setProperty("prism.order", "sw");
        System.setProperty("prism.text", "t2k");
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.startup(latch::countDown);
        } catch (IllegalStateException alreadyStarted) {
            Platform.runLater(latch::countDown);
        } catch (RuntimeException ex) {
            Assumptions.assumeTrue(false, "JavaFX toolkit not available: " + ex.getMessage());
            return;
        }
        if (!latch.await(5, TimeUnit.SECONDS)) {
            fail("JavaFX platform failed to start");
        }
    }

    @Test
    void doubleClickInvokesEditListenerWhenNotDragging() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> edited = new AtomicReference<>();

        Platform.runLater(() -> {
            QuestNodeView view = new QuestNodeView("quest-123", "Quest", 0, 0);
            view.setOnEdit(id -> {
                edited.set(id);
                latch.countDown();
            });

            MouseEvent doubleClick = new MouseEvent(
                    MouseEvent.MOUSE_CLICKED,
                    10,
                    10,
                    10,
                    10,
                    MouseButton.PRIMARY,
                    2,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    true,
                    new PickResult(view, 10, 10)
            );
            view.fireEvent(doubleClick);
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Double-click did not trigger edit callback");
        assertEquals("quest-123", edited.get());
    }
}
