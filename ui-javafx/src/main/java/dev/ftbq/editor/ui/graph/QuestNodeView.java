package dev.ftbq.editor.ui.graph;

import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.PaintConverter;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class QuestNodeView extends Pane {
    private static final Color DEFAULT_FILL_COLOR = Color.web("#2f4f4f");
    private static final Color DEFAULT_STROKE_COLOR = Color.web("#b0b0b0");
    private static final Color DEFAULT_LABEL_COLOR = Color.web("#e6e6e6");

    private static final CssMetaData<QuestNodeView, Paint> FILL_PAINT_META =
            new CssMetaData<>("-quest-node-fill", PaintConverter.getInstance(), DEFAULT_FILL_COLOR) {
                @Override
                public boolean isSettable(QuestNodeView node) {
                    return node.fillPaint == null || !node.fillPaint.isBound();
                }

                @Override
                public StyleableProperty<Paint> getStyleableProperty(QuestNodeView node) {
                    return node.fillPaint;
                }
            };

    private static final CssMetaData<QuestNodeView, Paint> STROKE_PAINT_META =
            new CssMetaData<>("-quest-node-stroke", PaintConverter.getInstance(), DEFAULT_STROKE_COLOR) {
                @Override
                public boolean isSettable(QuestNodeView node) {
                    return node.strokePaint == null || !node.strokePaint.isBound();
                }

                @Override
                public StyleableProperty<Paint> getStyleableProperty(QuestNodeView node) {
                    return node.strokePaint;
                }
            };

    private static final CssMetaData<QuestNodeView, Paint> LABEL_PAINT_META =
            new CssMetaData<>("-quest-node-label", PaintConverter.getInstance(), DEFAULT_LABEL_COLOR) {
                @Override
                public boolean isSettable(QuestNodeView node) {
                    return node.labelPaint == null || !node.labelPaint.isBound();
                }

                @Override
                public StyleableProperty<Paint> getStyleableProperty(QuestNodeView node) {
                    return node.labelPaint;
                }
            };

    private static final List<CssMetaData<? extends Styleable, ?>> CSS_META_DATA;

    static {
        List<CssMetaData<? extends Styleable, ?>> list = new ArrayList<>(Pane.getClassCssMetaData());
        list.add(FILL_PAINT_META);
        list.add(STROKE_PAINT_META);
        list.add(LABEL_PAINT_META);
        CSS_META_DATA = Collections.unmodifiableList(list);
    }

    public final String questId;
    private final GraphCanvas graphCanvas;
    private double worldX;
    private double worldY;
    private boolean selected;
    private boolean dragging;
    private boolean dragOccurred;

    // NEW: Double-click detection
    private long lastClickTime = 0;
    private static final int DOUBLE_CLICK_DELAY = 500;

    private Consumer<String> onEdit;
    private TriConsumer<String, Double, Double> onMove;

    private final Rectangle background;
    private final Label titleLabel;

    private final StyleableObjectProperty<Paint> fillPaint =
            new StyleableObjectProperty<>(DEFAULT_FILL_COLOR) {
                @Override
                public Object getBean() {
                    return QuestNodeView.this;
                }

                @Override
                public String getName() {
                    return "fillPaint";
                }

                @Override
                public CssMetaData<QuestNodeView, Paint> getCssMetaData() {
                    return FILL_PAINT_META;
                }
            };

    private final StyleableObjectProperty<Paint> strokePaint =
            new StyleableObjectProperty<>(DEFAULT_STROKE_COLOR) {
                @Override
                public Object getBean() {
                    return QuestNodeView.this;
                }

                @Override
                public String getName() {
                    return "strokePaint";
                }

                @Override
                public CssMetaData<QuestNodeView, Paint> getCssMetaData() {
                    return STROKE_PAINT_META;
                }
            };

    private final StyleableObjectProperty<Paint> labelPaint =
            new StyleableObjectProperty<>(DEFAULT_LABEL_COLOR) {
                @Override
                public Object getBean() {
                    return QuestNodeView.this;
                }

                @Override
                public String getName() {
                    return "labelPaint";
                }

                @Override
                public CssMetaData<QuestNodeView, Paint> getCssMetaData() {
                    return LABEL_PAINT_META;
                }
            };

    public QuestNodeView(String questId, String title, double worldX, double worldY, GraphCanvas graphCanvas) {
        this.questId = Objects.requireNonNull(questId, "questId");
        this.worldX = worldX;
        this.worldY = worldY;
        this.graphCanvas = Objects.requireNonNull(graphCanvas, "graphCanvas");

        getStyleClass().add("quest-node");

        background = new Rectangle(64, 64);
        background.setArcWidth(8);
        background.setArcHeight(8);
        background.fillProperty().bind(fillPaint);
        background.strokeProperty().bind(strokePaint);
        background.setStrokeWidth(2);

        titleLabel = new Label(title);
        titleLabel.textFillProperty().bind(labelPaint);
        titleLabel.setMaxWidth(120);
        titleLabel.setWrapText(true);
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.getStyleClass().add("quest-title");

        StackPane iconContainer = new StackPane(background);
        VBox layout = new VBox(4, iconContainer, titleLabel);
        layout.setAlignment(Pos.CENTER);

        getChildren().add(layout);

        setPickOnBounds(true);
        setMouseTransparent(false);

        setupEventHandlers();
        updateScreenPosition();
    }

    private void setupEventHandlers() {
        final double[] dragStart = new double[2];

        // ENHANCED: Click handling with double-click detection
        setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                long currentTime = System.currentTimeMillis();
                long timeSinceLastClick = currentTime - lastClickTime;

                if ((timeSinceLastClick < DOUBLE_CLICK_DELAY || event.getClickCount() > 1) && !dragging) {
                    // Double-click detected
                    System.out.println("Double-click detected on quest: " + questId);
                    if (onEdit != null) {
                        onEdit.accept(questId);
                    }
                    event.consume();
                    lastClickTime = 0;
                } else {
                    // Single click - let it propagate for selection
                    lastClickTime = currentTime;
                }
            }
        });

        setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY && !event.isShiftDown()) {
                dragStart[0] = event.getSceneX();
                dragStart[1] = event.getSceneY();
                dragging = false;
                event.consume();
            }
        });

        setOnMouseDragged(event -> {
            if (event.getButton() == MouseButton.PRIMARY && !event.isShiftDown()) {
                dragging = true;
                dragOccurred = true;
                double[] delta = graphCanvas.screenToWorldDelta(
                        dragStart[0], dragStart[1],
                        event.getSceneX(), event.getSceneY()
                );
                worldX += delta[0];
                worldY += delta[1];
                dragStart[0] = event.getSceneX();
                dragStart[1] = event.getSceneY();
                updateScreenPosition();
                event.consume();
            }
        });

        setOnMouseReleased(event -> {
            dragging = dragging || !event.isStillSincePress();
            if (dragging) {
                double[] releaseWorld = graphCanvas.screenToWorld(event.getScreenX(), event.getScreenY());
                if (releaseWorld.length == 2) {
                    if (Double.isFinite(releaseWorld[0])) {
                        worldX = releaseWorld[0];
                    }
                    if (Double.isFinite(releaseWorld[1])) {
                        worldY = releaseWorld[1];
                    }
                }
                if (dragOccurred && graphCanvas.isSnapToGrid()) {
                    worldX = graphCanvas.snap(worldX);
                    worldY = graphCanvas.snap(worldY);
                    updateScreenPosition();
                }
                if (onMove != null) {
                    onMove.accept(questId, worldX, worldY);
                }
                dragging = false;
                dragOccurred = false;
                event.consume();
            }
        });

        setOnMouseEntered(event -> {
            if (!dragging) {
                background.setStrokeWidth(3);
                setStyle("-fx-cursor: hand;");
            }
        });

        setOnMouseExited(event -> {
            if (!dragging) {
                background.setStrokeWidth(selected ? 3 : 2);
                setStyle("-fx-cursor: default;");
            }
        });
    }

    public void setWorldPosition(double x, double y) {
        this.worldX = x;
        this.worldY = y;
        updateScreenPosition();
    }

    public void updateScreenPosition() {
        double[] screen = graphCanvas.worldToScreen(worldX, worldY);
        setLayoutX(screen[0] - getWidth() / 2);
        setLayoutY(screen[1] - getHeight() / 2);
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        if (selected) {
            if (background.strokeProperty().isBound()) {
                background.strokeProperty().unbind();
            }
            background.setStroke(Color.web("#4a9eff"));
            background.setStrokeWidth(3);
        } else {
            if (!background.strokeProperty().isBound()) {
                background.strokeProperty().bind(strokePaint);
            }
            background.setStrokeWidth(2);
        }
    }

    public void setOnEdit(Consumer<String> callback) {
        this.onEdit = callback;
    }

    public void setOnMove(TriConsumer<String, Double, Double> callback) {
        this.onMove = callback;
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return CSS_META_DATA;
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }

    public StyleableObjectProperty<Paint> fillPaintProperty() {
        return fillPaint;
    }

    public Paint getFillPaint() {
        return fillPaint.get();
    }

    public void setFillPaint(Paint paint) {
        fillPaint.set(paint);
    }

    public StyleableObjectProperty<Paint> strokePaintProperty() {
        return strokePaint;
    }

    public Paint getStrokePaint() {
        return strokePaint.get();
    }

    public void setStrokePaint(Paint paint) {
        strokePaint.set(paint);
    }

    public StyleableObjectProperty<Paint> labelPaintProperty() {
        return labelPaint;
    }

    public Paint getLabelPaint() {
        return labelPaint.get();
    }

    public void setLabelPaint(Paint paint) {
        labelPaint.set(paint);
    }

    @FunctionalInterface
    public interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }
}