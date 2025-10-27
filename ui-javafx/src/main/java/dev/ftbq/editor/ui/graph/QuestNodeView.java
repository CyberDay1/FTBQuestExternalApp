package dev.ftbq.editor.ui.graph;

import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;

public class QuestNodeView extends Pane {
    public final String questId;
    private double worldX;
    private double worldY;
    private double dragDX, dragDY;
    private final Circle body = new Circle(18);
    private final Text label = new Text();

    public interface MoveListener {
        void moved(String questId, double screenX, double screenY);
    }

    private MoveListener onMove;

    public QuestNodeView(String questId, String title, double worldX, double worldY) {
        this.questId = questId;
        this.worldX = worldX;
        this.worldY = worldY;
        body.setFill(Color.web("#2f4f4f"));
        body.setStroke(Color.web("#b0b0b0"));
        label.setText(title);
        label.setFill(Color.web("#e6e6e6"));
        label.setMouseTransparent(true);
        label.layoutBoundsProperty().addListener((obs, oldBounds, newBounds) -> {
            label.setLayoutX(-newBounds.getWidth() / 2);
            label.setLayoutY(newBounds.getHeight() / 4);
        });
        getChildren().addAll(body, label);
        setPickOnBounds(false);
        updateScreenPosition(worldX, worldY);
        enableDrag();
    }

    public void setOnMove(MoveListener listener) {
        this.onMove = listener;
    }

    public double getWorldX() {
        return worldX;
    }

    public double getWorldY() {
        return worldY;
    }

    public void setWorldPosition(double x, double y) {
        this.worldX = x;
        this.worldY = y;
    }

    public void updateScreenPosition(double screenX, double screenY) {
        relocate(screenX - body.getRadius(), screenY - body.getRadius());
    }

    public void setSelected(boolean selected) {
        body.setStroke(selected ? Color.web("#ffd37f") : Color.web("#b0b0b0"));
        body.setStrokeWidth(selected ? 2.4 : 1.0);
    }

    private void enableDrag() {
        addEventFilter(MouseEvent.MOUSE_PRESSED, e -> {
            dragDX = e.getX();
            dragDY = e.getY();
        });
        addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            double nx = getLayoutX() + (e.getX() - dragDX);
            double ny = getLayoutY() + (e.getY() - dragDY);
            relocate(nx, ny);
        });
        addEventFilter(MouseEvent.MOUSE_RELEASED, e -> {
            double nx = getLayoutX() + body.getRadius();
            double ny = getLayoutY() + body.getRadius();
            if (onMove != null) {
                onMove.moved(questId, nx, ny);
            }
        });
    }
}
