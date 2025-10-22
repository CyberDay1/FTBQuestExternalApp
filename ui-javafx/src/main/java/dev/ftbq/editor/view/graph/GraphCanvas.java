package dev.ftbq.editor.view.graph;

import dev.ftbq.editor.domain.BackgroundAlignment;
import dev.ftbq.editor.domain.BackgroundRef;
import dev.ftbq.editor.domain.BackgroundRepeat;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.Quest;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.control.Label;
import javafx.geometry.Pos;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Canvas based quest graph with pan/zoom and node interaction support.
 */
public class GraphCanvas extends Pane {

    public enum ValidationLevel {
        OK,
        WARNING,
        ERROR
    }

    private static final double NODE_SIZE = 72;
    private static final double MIN_SCALE = 0.4;
    private static final double MAX_SCALE = 2.6;

    private final ObjectProperty<Chapter> chapter = new SimpleObjectProperty<>();
    private final Canvas backgroundLayer = new Canvas();
    private final Canvas edgeLayer = new Canvas();
    private final Pane nodeLayer = new Pane();
    private final Group contentGroup = new Group();
    private final Scale scaleTransform = new Scale(1, 1);
    private final Translate translateTransform = new Translate();

    private final Map<String, QuestNodeView> nodeViews = new HashMap<>();
    private final ObservableList<GraphEdge> edges = FXCollections.observableArrayList();
    private final Map<String, ValidationLevel> validationStateByQuest = new HashMap<>();
    private QuestGraphModel model;

    private double currentScale = 1.0;
    private boolean panning;
    private Point2D panAnchor;
    private Point2D panStartTranslate;

    public GraphCanvas() {
        getStyleClass().add("quest-graph-canvas");
        setPrefSize(1024, 768);
        setMinSize(0, 0);
        nodeLayer.setPickOnBounds(false);
        backgroundLayer.setMouseTransparent(true);
        edgeLayer.setMouseTransparent(true);

        contentGroup.getChildren().addAll(backgroundLayer, edgeLayer, nodeLayer);
        contentGroup.getTransforms().addAll(scaleTransform, translateTransform);
        getChildren().add(contentGroup);

        widthProperty().addListener((obs, oldV, newV) -> resizeLayers());
        heightProperty().addListener((obs, oldV, newV) -> resizeLayers());

        addEventFilter(MouseEvent.MOUSE_PRESSED, this::handleMousePressed);
        addEventFilter(MouseEvent.MOUSE_DRAGGED, this::handleMouseDragged);
        addEventFilter(MouseEvent.MOUSE_RELEASED, this::handleMouseReleased);
        addEventFilter(ScrollEvent.SCROLL, this::handleScroll);

        chapter.addListener((obs, oldChapter, newChapter) -> rebuildGraph());
    }

    public ObjectProperty<Chapter> chapterProperty() {
        return chapter;
    }

    public void setChapter(Chapter value) {
        chapter.set(value);
    }

    public Chapter getChapter() {
        return chapter.get();
    }

    public void setValidationState(String questId, ValidationLevel level) {
        Objects.requireNonNull(questId, "questId");
        validationStateByQuest.put(questId, Objects.requireNonNull(level, "level"));
        if (model != null) {
            model.updateValidation(questId, level);
        }
        QuestNodeView node = nodeViews.get(questId);
        if (node != null) {
            node.setValidationLevel(level);
        }
    }

    public void clearValidationStates() {
        validationStateByQuest.clear();
        if (model != null) {
            model.getNodes().forEach(node -> model.updateValidation(node.getQuest().id(), ValidationLevel.OK));
        }
        nodeViews.values().forEach(node -> node.setValidationLevel(ValidationLevel.OK));
    }

    public int getNodeCount() {
        return nodeViews.size();
    }

    public ObservableList<GraphEdge> getEdges() {
        return FXCollections.unmodifiableObservableList(edges);
    }

    public Optional<QuestNodeView> findNode(String questId) {
        return Optional.ofNullable(nodeViews.get(questId));
    }

    public ValidationLevel getValidationState(String questId) {
        return validationStateByQuest.getOrDefault(questId, ValidationLevel.OK);
    }

    public void relocateNode(String questId, double layoutX, double layoutY) {
        QuestNodeView node = nodeViews.get(questId);
        if (node == null) {
            return;
        }
        node.relocate(layoutX, layoutY);
        node.getModelNode().setPosition(layoutX, layoutY);
        if (model != null) {
            model.moveNode(questId, layoutX, layoutY);
        }
        drawEdges();
    }

    private void resizeLayers() {
        double width = Math.max(getWidth(), 1);
        double height = Math.max(getHeight(), 1);
        backgroundLayer.setWidth(width);
        backgroundLayer.setHeight(height);
        edgeLayer.setWidth(width);
        edgeLayer.setHeight(height);
        drawBackground();
        drawEdges();
    }

    private void rebuildGraph() {
        nodeLayer.getChildren().clear();
        nodeViews.clear();
        edges.clear();

        Chapter activeChapter = chapter.get();
        if (activeChapter == null) {
            model = null;
            drawBackground();
            return;
        }

        model = QuestGraphModel.fromChapter(activeChapter, validationStateByQuest);

        for (QuestGraphModel.Node node : model.getNodes()) {
            QuestNodeView view = new QuestNodeView(node);
            view.relocate(node.getPosition().getX(), node.getPosition().getY());
            installNodeHandlers(view);
            nodeLayer.getChildren().add(view);
            nodeViews.put(node.getQuest().id(), view);
        }

        for (QuestGraphModel.Edge edgeModel : model.getEdges()) {
            QuestNodeView sourceView = nodeViews.get(edgeModel.getSource().getQuest().id());
            QuestNodeView targetView = nodeViews.get(edgeModel.getTarget().getQuest().id());
            if (sourceView != null && targetView != null) {
                edges.add(new GraphEdge(edgeModel, sourceView, targetView));
            }
        }

        drawBackground();
        drawEdges();
    }

    private void installNodeHandlers(QuestNodeView view) {
        view.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            view.requestFocus();
            view.startDrag(contentGroup.sceneToLocal(event.getSceneX(), event.getSceneY()));
            event.consume();
        });

        view.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (!event.isPrimaryButtonDown()) {
                return;
            }
            Point2D current = contentGroup.sceneToLocal(event.getSceneX(), event.getSceneY());
            Point2D delta = current.subtract(view.getDragAnchor());
            double newX = view.getInitialLayout().getX() + delta.getX();
            double newY = view.getInitialLayout().getY() + delta.getY();
            view.relocate(newX, newY);
            view.getModelNode().setPosition(newX, newY);
            if (model != null) {
                model.moveNode(view.getModelNode().getQuest().id(), newX, newY);
            }
            drawEdges();
            event.consume();
        });

        view.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                view.endDrag();
                event.consume();
            }
        });
    }

    private void handleMousePressed(MouseEvent event) {
        if (event.getTarget() instanceof QuestNodeView) {
            return;
        }
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        panning = true;
        panAnchor = new Point2D(event.getX(), event.getY());
        panStartTranslate = new Point2D(translateTransform.getX(), translateTransform.getY());
        event.consume();
    }

    private void handleMouseDragged(MouseEvent event) {
        if (!panning) {
            return;
        }
        Point2D current = new Point2D(event.getX(), event.getY());
        Point2D delta = current.subtract(panAnchor);
        translateTransform.setX(panStartTranslate.getX() + delta.getX());
        translateTransform.setY(panStartTranslate.getY() + delta.getY());
        event.consume();
    }

    private void handleMouseReleased(MouseEvent event) {
        if (panning && event.getButton() == MouseButton.PRIMARY) {
            panning = false;
            event.consume();
        }
    }

    private void handleScroll(ScrollEvent event) {
        if (event.getDeltaY() == 0) {
            return;
        }
        double zoomFactor = Math.exp(event.getDeltaY() / 400.0);
        double newScale = clamp(currentScale * zoomFactor, MIN_SCALE, MAX_SCALE);
        double scaleChange = newScale / currentScale;
        Point2D pivot = new Point2D(event.getX(), event.getY());
        double newTranslateX = pivot.getX() - scaleChange * (pivot.getX() - translateTransform.getX());
        double newTranslateY = pivot.getY() - scaleChange * (pivot.getY() - translateTransform.getY());
        translateTransform.setX(newTranslateX);
        translateTransform.setY(newTranslateY);
        currentScale = newScale;
        scaleTransform.setX(currentScale);
        scaleTransform.setY(currentScale);
        event.consume();
    }

    private void drawEdges() {
        GraphicsContext gc = edgeLayer.getGraphicsContext2D();
        gc.clearRect(0, 0, edgeLayer.getWidth(), edgeLayer.getHeight());
        gc.setLineWidth(3);

        for (GraphEdge edge : edges) {
            Point2D start = edge.getSource().getCenter();
            Point2D end = edge.getTarget().getCenter();
            edge.setStart(start);
            edge.setEnd(end);
            gc.setStroke(edge.isRequired() ? Color.web("#ffcc33") : Color.web("#66aaff"));
            gc.strokeLine(start.getX(), start.getY(), end.getX(), end.getY());
            drawArrow(gc, start, end);
        }
    }

    private void drawArrow(GraphicsContext gc, Point2D start, Point2D end) {
        double arrowLength = 12;
        double arrowWidth = 6;
        double angle = Math.atan2(end.getY() - start.getY(), end.getX() - start.getX());
        double sin = Math.sin(angle);
        double cos = Math.cos(angle);
        double x1 = end.getX() - arrowLength * cos + arrowWidth * sin;
        double y1 = end.getY() - arrowLength * sin - arrowWidth * cos;
        double x2 = end.getX() - arrowLength * cos - arrowWidth * sin;
        double y2 = end.getY() - arrowLength * sin + arrowWidth * cos;
        gc.strokeLine(end.getX(), end.getY(), x1, y1);
        gc.strokeLine(end.getX(), end.getY(), x2, y2);
    }

    private void drawBackground() {
        GraphicsContext gc = backgroundLayer.getGraphicsContext2D();
        gc.clearRect(0, 0, backgroundLayer.getWidth(), backgroundLayer.getHeight());
        Chapter active = chapter.get();
        if (active == null) {
            gc.setFill(Color.web("#1e1e1e"));
            gc.fillRect(0, 0, backgroundLayer.getWidth(), backgroundLayer.getHeight());
            return;
        }
        BackgroundRef background = active.background();
        Paint basePaint = paintFromBackground(background);
        BackgroundRepeat repeat = background.repeat().orElse(BackgroundRepeat.BOTH);
        if (repeat == BackgroundRepeat.NONE) {
            gc.setFill(basePaint);
            gc.fillRect(0, 0, backgroundLayer.getWidth(), backgroundLayer.getHeight());
            return;
        }

        double tileSize = 128;
        int tilesX = (int) Math.ceil(backgroundLayer.getWidth() / tileSize) + 1;
        int tilesY = (int) Math.ceil(backgroundLayer.getHeight() / tileSize) + 1;
        double offsetX = alignmentOffset(background.alignment(), backgroundLayer.getWidth(), tileSize, true);
        double offsetY = alignmentOffset(background.alignment(), backgroundLayer.getHeight(), tileSize, false);

        for (int x = -1; x < tilesX; x++) {
            for (int y = -1; y < tilesY; y++) {
                if (repeat == BackgroundRepeat.HORIZONTAL && y != 0) {
                    continue;
                }
                if (repeat == BackgroundRepeat.VERTICAL && x != 0) {
                    continue;
                }
                double drawX = x * tileSize + offsetX;
                double drawY = y * tileSize + offsetY;
                gc.setFill(basePaint);
                gc.fillRect(drawX, drawY, tileSize, tileSize);
                gc.setStroke(((Color) basePaint).darker().deriveColor(0, 1, 0.8, 0.4));
                gc.strokeRect(drawX, drawY, tileSize, tileSize);
            }
        }
    }

    private double alignmentOffset(Optional<BackgroundAlignment> alignment, double canvasLength, double tileSize, boolean horizontal) {
        if (alignment.isEmpty()) {
            return 0;
        }
        BackgroundAlignment value = alignment.get();
        return switch (value) {
            case CENTER -> (canvasLength - tileSize) / 2;
            case TOP_LEFT -> horizontal ? 0 : 0;
            case TOP_RIGHT -> horizontal ? canvasLength - tileSize : 0;
            case BOTTOM_LEFT -> horizontal ? 0 : canvasLength - tileSize;
            case BOTTOM_RIGHT -> horizontal ? canvasLength - tileSize : canvasLength - tileSize;
            case TOP -> horizontal ? (canvasLength - tileSize) / 2 : 0;
            case BOTTOM -> horizontal ? (canvasLength - tileSize) / 2 : canvasLength - tileSize;
            case LEFT -> horizontal ? 0 : (canvasLength - tileSize) / 2;
            case RIGHT -> horizontal ? canvasLength - tileSize : (canvasLength - tileSize) / 2;
        };
    }

    private Paint paintFromBackground(BackgroundRef background) {
        Color primary = colorFromString(background.texture());
        Color secondary = primary.deriveColor(180, 1.1, 0.7, 1.0);
        return new CheckerboardPaint(primary, secondary, 16).create();
    }

    private static Color colorFromString(String value) {
        int hash = value.hashCode();
        double hue = (hash & 0xFF) / 255.0 * 360.0;
        double saturation = 0.35 + ((hash >> 8) & 0x3F) / 255.0 * 0.45;
        double brightness = 0.55 + ((hash >> 16) & 0x3F) / 255.0 * 0.35;
        return Color.hsb(hue, saturation, brightness);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class GraphEdge {
        private final QuestGraphModel.Edge edgeModel;
        private final QuestNodeView source;
        private final QuestNodeView target;
        private Point2D start = Point2D.ZERO;
        private Point2D end = Point2D.ZERO;

        private GraphEdge(QuestGraphModel.Edge edgeModel, QuestNodeView source, QuestNodeView target) {
            this.edgeModel = Objects.requireNonNull(edgeModel, "edgeModel");
            this.source = Objects.requireNonNull(source, "source");
            this.target = Objects.requireNonNull(target, "target");
        }

        public QuestNodeView getSource() {
            return source;
        }

        public QuestNodeView getTarget() {
            return target;
        }

        public boolean isRequired() {
            return edgeModel.isRequired();
        }

        public String getSourceQuestId() {
            return edgeModel.getSource().getQuest().id();
        }

        public String getTargetQuestId() {
            return edgeModel.getTarget().getQuest().id();
        }

        public Point2D getStart() {
            return start;
        }

        public Point2D getEnd() {
            return end;
        }

        private void setStart(Point2D start) {
            this.start = start;
        }

        private void setEnd(Point2D end) {
            this.end = end;
        }
    }

    public static final class QuestNodeView extends Pane {
        private final QuestGraphModel.Node modelNode;
        private final Circle badge = new Circle(9);
        private ValidationLevel currentLevel = ValidationLevel.OK;

        private Point2D dragAnchor = Point2D.ZERO;
        private Point2D initialLayout = Point2D.ZERO;

        private QuestNodeView(QuestGraphModel.Node modelNode) {
            this.modelNode = modelNode;
            setPrefSize(NODE_SIZE, NODE_SIZE);
            setMinSize(NODE_SIZE, NODE_SIZE);
            setMaxSize(NODE_SIZE, NODE_SIZE);
            setPickOnBounds(false);
            getStyleClass().add("quest-node");

            Circle iconCircle = new Circle(NODE_SIZE / 2 - 6);
            iconCircle.setFill(colorFromString(modelNode.getQuest().icon().icon()));
            iconCircle.setStroke(Color.web("#202020"));
            iconCircle.setStrokeWidth(2);
            iconCircle.setMouseTransparent(true);

            Label iconLabel = new Label(shortIconName(modelNode.getQuest()));
            iconLabel.setTextFill(Color.WHITE);
            iconLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
            iconLabel.setMouseTransparent(true);

            badge.setStroke(Color.web("#1f1f1f"));
            badge.setStrokeWidth(2);
            badge.setMouseTransparent(true);

            StackPane stack = new StackPane(iconCircle, iconLabel, badge);
            stack.setPrefSize(NODE_SIZE, NODE_SIZE);
            stack.setMinSize(NODE_SIZE, NODE_SIZE);
            stack.setMaxSize(NODE_SIZE, NODE_SIZE);
            StackPane.setAlignment(iconCircle, Pos.CENTER);
            StackPane.setAlignment(iconLabel, Pos.CENTER);
            StackPane.setAlignment(badge, Pos.TOP_RIGHT);
            badge.setTranslateX(-10);
            badge.setTranslateY(10);

            getChildren().add(stack);
            setValidationLevel(modelNode.getValidationLevel());
        }

        public QuestGraphModel.Node getModelNode() {
            return modelNode;
        }

        public void setValidationLevel(ValidationLevel level) {
            currentLevel = level;
            badge.setFill(validationColor(level));
            modelNode.setValidation(level);
        }

        public ValidationLevel getValidationLevel() {
            return currentLevel;
        }

        public Point2D getCenter() {
            return new Point2D(getLayoutX() + NODE_SIZE / 2, getLayoutY() + NODE_SIZE / 2);
        }

        public void startDrag(Point2D anchor) {
            dragAnchor = anchor;
            initialLayout = new Point2D(getLayoutX(), getLayoutY());
        }

        public void endDrag() {
            dragAnchor = Point2D.ZERO;
        }

        public Point2D getDragAnchor() {
            return dragAnchor;
        }

        public Point2D getInitialLayout() {
            return initialLayout;
        }

        private Paint validationColor(ValidationLevel level) {
            return switch (level) {
                case OK -> Color.web("#6fc276");
                case WARNING -> Color.web("#f1c40f");
                case ERROR -> Color.web("#e74c3c");
            };
        }

        private String shortIconName(Quest quest) {
            String icon = quest.icon().icon();
            int separator = icon.lastIndexOf(':');
            if (separator >= 0 && separator < icon.length() - 1) {
                icon = icon.substring(separator + 1);
            }
            if (icon.length() <= 4) {
                return icon.toUpperCase(Locale.ENGLISH);
            }
            return icon.substring(0, 4).toUpperCase(Locale.ENGLISH);
        }
    }

    private static final class CheckerboardPaint {
        private final Color a;
        private final Color b;
        private final int squareSize;

        private CheckerboardPaint(Color a, Color b, int squareSize) {
            this.a = a;
            this.b = b;
            this.squareSize = squareSize;
        }

        private Paint create() {
            int size = squareSize * 2;
            javafx.scene.image.WritableImage image = new javafx.scene.image.WritableImage(size, size);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    boolean even = ((x / squareSize) + (y / squareSize)) % 2 == 0;
                    image.getPixelWriter().setColor(x, y, even ? a : b);
                }
            }
            return new javafx.scene.paint.ImagePattern(image, 0, 0, size, size, false);
        }
    }
}
