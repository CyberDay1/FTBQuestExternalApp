package dev.ftbq.editor.ui.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javafx.application.Platform;
import javafx.event.EventType;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class QuestNodeViewTest {

    private Stage stage;

    @Start
    private void start(Stage stage) {
        this.stage = stage;
        stage.setScene(new Scene(new Group(), 200, 200));
        stage.show();
    }

    @Test
    void doubleClickInvokesEditListener() {
        QuestNodeView node = new QuestNodeView("quest1", "Quest", 48, 48);
        var edited = new java.util.concurrent.atomic.AtomicReference<String>();
        node.setOnEdit(edited::set);

        Platform.runLater(() -> {
            stage.getScene().setRoot(new Group(node));
            node.fireEvent(mouseEvent(MouseEvent.MOUSE_PRESSED, 18, 18, 18, 18, 1, true));
            node.fireEvent(mouseEvent(MouseEvent.MOUSE_CLICKED, 18, 18, 18, 18, 2, true));
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("quest1", edited.get(), "Double click should trigger edit callback");
    }

    @Test
    void releasingNodePersistsScreenCoordinates() {
        QuestNodeView node = new QuestNodeView("quest2", "Quest", 32, 32);
        var moved = new java.util.concurrent.atomic.AtomicReference<Point2D>();
        node.setOnMove((id, x, y) -> moved.set(new Point2D(x, y)));

        Platform.runLater(() -> {
            stage.getScene().setRoot(new Group(node));
            node.relocate(70, 90);
            node.fireEvent(mouseEvent(MouseEvent.MOUSE_RELEASED, 18, 18, 88, 108, 1, false));
        });
        WaitForAsyncUtils.waitForFxEvents();

        Point2D point = moved.get();
        assertNotNull(point, "Releasing node should persist its position");
        assertEquals(88.0, point.getX());
        assertEquals(108.0, point.getY());
    }

    private MouseEvent mouseEvent(EventType<MouseEvent> type,
                                   double x,
                                   double y,
                                   double screenX,
                                   double screenY,
                                   int clickCount,
                                   boolean stillSincePress) {
        return new MouseEvent(type,
                x,
                y,
                screenX,
                screenY,
                MouseButton.PRIMARY,
                clickCount,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                stillSincePress,
                null);
    }
}
