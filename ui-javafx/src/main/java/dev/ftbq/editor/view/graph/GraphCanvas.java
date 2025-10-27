package dev.ftbq.editor.view.graph;

import dev.ftbq.editor.domain.BackgroundAlignment;
import dev.ftbq.editor.domain.BackgroundRef;
import dev.ftbq.editor.domain.BackgroundRepeat;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.assets.CacheManager;
import dev.ftbq.editor.services.UiServiceLocator;
import dev.ftbq.editor.services.bus.ServiceLocator;
import dev.ftbq.editor.services.logging.StructuredLogger;
import dev.ftbq.editor.store.StoreDao;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableSet;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.Group;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.transform.Affine;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;
import javafx.geometry.Pos;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Canvas based quest graph with pan/zoom and node interaction support.
 *
 * Consumers can register a double-click handler to respond when a quest node
 * is activated, enabling inline editing within the chapter editor surface.
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
    private static final BackgroundRef DEFAULT_BACKGROUND = new BackgroundRef("minecraft:textures/gui/default.png");

    private static final double MINIMAP_WIDTH = 200;
    private static final double MINIMAP_HEIGHT = 120;

    private final Canvas backgroundLayer = new Canvas();
    private final Canvas edgeLayer = new Canvas();
    private final Pane nodeLayer = new Pane();
    private final Canvas minimapLayer = new Canvas(MINIMAP_WIDTH, MINIMAP_HEIGHT);
    private final Pane overlayLayer = new Pane();
    private final Group contentGroup = new Group();
    private final Scale scaleTransform = new Scale(1, 1);
    private final Translate translateTransform = new Translate();

    private final ObjectProperty<BackgroundRef> backgroundRef = new SimpleObjectProperty<>(DEFAULT_BACKGROUND);
    private final Map<String, QuestNodeView> nodeViews = new HashMap<>();
    private final ObservableList<GraphEdge> edges = FXCollections.observableArrayList();
    private final Map<String, ValidationLevel> validationStateByQuest = new HashMap<>();
    private final Map<String, Image> backgroundImages = new HashMap<>();
    private QuestGraphModel model;
    private final StructuredLogger logger = ServiceLocator.loggerFactory().create(GraphCanvas.class);
    private final ObjectProperty<QuestGraphModel.Node> selectedNode = new SimpleObjectProperty<>();
    private final ObservableSet<QuestNodeView> selectedNodes = FXCollections.observableSet(new LinkedHashSet<>());
    private QuestNodeView primarySelection;
    private Consumer<QuestGraphModel.Node> nodeDoubleClickHandler;

    private double currentScale = 1.0;
    private boolean panning;
    private Point2D panAnchor;
    private Point2D panStartTranslate;
    private boolean bandSelecting;
    private boolean bandExtending;
    private Point2D bandAnchor = Point2D.ZERO;
    private final Rectangle selectionRectangle = new Rectangle();

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

        minimapLayer.setManaged(false);
        minimapLayer.setMouseTransparent(true);
        minimapLayer.getGraphicsContext2D().setFill(Color.color(0, 0, 0, 0.6));

        overlayLayer.setManaged(false);
        overlayLayer.setMouseTransparent(true);
        overlayLayer.setPickOnBounds(false);
        selectionRectangle.setManaged(false);
        selectionRectangle.setVisible(false);
        selectionRectangle.setStroke(Color.web("#4a90e2"));
        selectionRectangle.setFill(Color.color(0.29, 0.56, 0.91, 0.2));
        selectionRectangle.getStrokeDashArray().setAll(6.0, 4.0);
        overlayLayer.getChildren().addAll(minimapLayer, selectionRectangle);
        getChildren().add(overlayLayer);

        widthProperty().addListener((obs, oldV, newV) -> resizeLayers());
        heightProperty().addListener((obs, oldV, newV) -> resizeLayers());

        addEventFilter(MouseEvent.MOUSE_PRESSED, this::handleMousePressed);
        addEventFilter(MouseEvent.MOUSE_DRAGGED, this::handleMouseDragged);
        addEventFilter(MouseEvent.MOUSE_RELEASED, this::handleMouseReleased);
        addEventFilter(ScrollEvent.SCROLL, this::handleScroll);

        chapter.addListener((obs, oldChapter, newChapter) -> {
            if (newChapter != null) {
                backgroundRef.set(Objects.requireNonNullElse(newChapter.background(), DEFAULT_BACKGROUND));
            } else {
                backgroundRef.set(DEFAULT_BACKGROUND);
            }
            rebuildGraph();
        });
        backgroundRef.addListener((obs, oldBackground, newBackground) -> {
            backgroundImages.clear();
            drawBackground();
        });
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

    public Quest getSelectedQuest() {
        QuestGraphModel.Node node = selectedNode.get();
        return node == null ? null : node.getQuest();
    }

    public void selectQuest(String questId) {
        if (questId == null) {
            clearSelection();
            return;
        }
        QuestNodeView view = nodeViews.get(questId);
        if (view != null) {
            setSelectedNode(view);
        }
    }

    public void selectQuests(Collection<String> questIds) {
        clearSelection();
        if (questIds == null) {
            return;
        }
        boolean makePrimary = true;
        for (String questId : questIds) {
            QuestNodeView view = nodeViews.get(questId);
            if (view != null) {
                addToSelection(view, makePrimary);
                makePrimary = false;
            }
        }
    }

    public void clearSelection() {
        selectedNodes.forEach(node -> node.setSelected(false));
        selectedNodes.clear();
        primarySelection = null;
        selectedNode.set(null);
    }

    public void setOnNodeDoubleClick(Consumer<QuestGraphModel.Node> handler) {
        this.nodeDoubleClickHandler = handler;
    }

    private void setSelectedNode(QuestNodeView view) {
        if (view == null) {
            clearSelection();
            return;
        }
        clearSelection();
        addToSelection(view, true);
    }

    private void addToSelection(QuestNodeView view, boolean makePrimary) {
        if (view == null) {
            return;
        }
        if (selectedNodes.add(view)) {
            view.setSelected(true);
        }
        if (makePrimary || primarySelection == null) {
            primarySelection = view;
            selectedNode.set(view.getModelNode());
        }
    }

    private void removeFromSelection(QuestNodeView view) {
        if (view == null) {
            return;
        }
        if (selectedNodes.remove(view)) {
            view.setSelected(false);
        }
        if (view == primarySelection) {
            primarySelection = selectedNodes.stream().findFirst().orElse(null);
            selectedNode.set(primarySelection != null ? primarySelection.getModelNode() : null);
        }
    }

    private void toggleSelection(QuestNodeView view) {
        if (view == null) {
            return;
        }
        if (selectedNodes.contains(view)) {
            removeFromSelection(view);
        } else {
            addToSelection(view, true);
        }
    }

    public void alignSelectionLeft() {
        if (selectedNodes.isEmpty()) {
            return;
        }
        double target = selectedNodes.stream()
                .mapToDouble(QuestNodeView::getLayoutX)
                .min()
                .orElse(0);
        selectedNodes.forEach(node -> updateNodePosition(node, target, node.getLayoutY()));
        drawEdges();
        updateMinimap();
    }

    public void alignSelectionRight() {
        if (selectedNodes.isEmpty()) {
            return;
        }
        double target = selectedNodes.stream()
                .mapToDouble(node -> node.getLayoutX() + NODE_SIZE)
                .max()
                .orElse(0);
        selectedNodes.forEach(node -> updateNodePosition(node, target - NODE_SIZE, node.getLayoutY()));
        drawEdges();
        updateMinimap();
    }

    public void alignSelectionTop() {
        if (selectedNodes.isEmpty()) {
            return;
        }
        double target = selectedNodes.stream()
                .mapToDouble(QuestNodeView::getLayoutY)
                .min()
                .orElse(0);
        selectedNodes.forEach(node -> updateNodePosition(node, node.getLayoutX(), target));
        drawEdges();
        updateMinimap();
    }

    public void alignSelectionCenter() {
        if (selectedNodes.isEmpty()) {
            return;
        }
        double target = selectedNodes.stream()
                .mapToDouble(node -> node.getLayoutX() + NODE_SIZE / 2.0)
                .average()
                .orElse(0);
        selectedNodes.forEach(node -> updateNodePosition(node, target - NODE_SIZE / 2.0, node.getLayoutY()));
        drawEdges();
        updateMinimap();
    }

    private void updateNodePosition(QuestNodeView node, double newX, double newY) {
        node.relocate(newX, newY);
        node.getModelNode().setPosition(newX, newY);
        if (model != null) {
            model.moveNode(node.getModelNode().getQuest().id(), newX, newY);
        }
        persistQuestPosition(node.getModelNode().getQuest().id(), newX, newY);
    }

    public ObjectProperty<BackgroundRef> backgroundRefProperty() {
        return backgroundRef;
    }

    public BackgroundRef getBackgroundRef() {
        return backgroundRef.get();
    }

    public void setBackgroundRef(BackgroundRef background) {
        backgroundRef.set(background == null ? DEFAULT_BACKGROUND : background);
    }

    public void setValidationState(String questId, ValidationLevel level) {
        Objects.requireNonNull(questId, "questId");
        validationStateByQuest.put(questId, Objects.requireNonNull(level, "level"));
        logger.info("Validation state updated",
                StructuredLogger.field("questId", questId),
                StructuredLogger.field("level", level));
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
        logger.info("Validation states cleared");
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
        persistQuestPosition(questId, layoutX, layoutY);
        updateMinimap();
    }

    private void resizeLayers() {
        double width = Math.max(getWidth(), 1);
        double height = Math.max(getHeight(), 1);
        backgroundLayer.setWidth(width);
        backgroundLayer.setHeight(height);
        edgeLayer.setWidth(width);
        edgeLayer.setHeight(height);
        overlayLayer.resizeRelocate(0, 0, width, height);
        minimapLayer.relocate(width - MINIMAP_WIDTH - 16, height - MINIMAP_HEIGHT - 16);
        drawBackground();
        drawEdges();
        updateMinimap();
    }

    public void rebuildGraph() {
        // TODO: Verify that CacheManager stores icons for all items ingested. If the cache does not
        // contain an icon for a quest, display the colored circle as a fallback but avoid repeatedly
        // attempting to fetch the missing icon. Consider caching negative lookups or storing this state.
        clearSelection();
        nodeLayer.getChildren().clear();
        nodeViews.clear();
        edges.clear();

        Chapter activeChapter = chapter.get();
        if (activeChapter == null) {
            model = null;
            drawBackground();
            return;
        }

        Map<String, Point2D> persistedPositions = loadPersistedPositions(activeChapter);
        model = QuestGraphModel.fromChapter(activeChapter, validationStateByQuest, persistedPositions);
        backgroundRef.set(activeChapter.background());

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
        updateMinimap();
    }

    private Map<String, Point2D> loadPersistedPositions(Chapter activeChapter) {
        StoreDao dao = UiServiceLocator.storeDao;
        if (dao == null || activeChapter.quests().isEmpty()) {
            return Map.of();
        }
        List<String> questIds = activeChapter.quests().stream()
                .map(Quest::id)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (questIds.isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, StoreDao.QuestPosition> stored = dao.findQuestPositions(questIds);
            if (stored.isEmpty()) {
                return Map.of();
            }
            Map<String, Point2D> positions = new HashMap<>();
            stored.forEach((questId, position) ->
                    positions.put(questId, new Point2D(position.x(), position.y())));
            return positions;
        } catch (RuntimeException e) {
            logger.error("Failed to load quest positions", e);
            return Map.of();
        }
    }

    private void persistQuestPosition(String questId, double x, double y) {
        if (questId == null) {
            return;
        }
        StoreDao dao = UiServiceLocator.storeDao;
        if (dao == null) {
            return;
        }
        try {
            dao.saveQuestPosition(questId, x, y);
        } catch (RuntimeException e) {
            logger.error("Failed to persist quest position", e,
                    StructuredLogger.field("questId", questId),
                    StructuredLogger.field("x", x),
                    StructuredLogger.field("y", y));
        }
    }

    private void installNodeHandlers(QuestNodeView view) {
        view.setOnMousePressed(event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            if (event.isControlDown() || event.isMetaDown()) {
                toggleSelection(view);
            } else if (event.isShiftDown()) {
                addToSelection(view, true);
            } else {
                setSelectedNode(view);
            }
            view.requestFocus();
            if (selectedNodes.contains(view)) {
                view.startDrag(contentGroup.sceneToLocal(event.getSceneX(), event.getSceneY()));
            }
            event.consume();
        });

        view.setOnMouseDragged(event -> {
            if (!event.isPrimaryButtonDown()) {
                return;
            }
            if (!selectedNodes.contains(view)) {
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
            persistQuestPosition(view.getModelNode().getQuest().id(), newX, newY);
            updateMinimap();
            event.consume();
        });

        view.setOnMouseReleased(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                view.endDrag();
                event.consume();
            }
        });

        view.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                setSelectedNode(view);
                Quest quest = view.getModelNode().getQuest();
                if (quest == null) {
                    logger.warn("Quest node missing quest model");
                    event.consume();
                    return;
                }
                if (nodeDoubleClickHandler != null) {
                    try {
                        nodeDoubleClickHandler.accept(view.getModelNode());
                    } catch (RuntimeException ex) {
                        logger.error("Quest node double-click handler failed", ex,
                                StructuredLogger.field("questId", quest.id()));
                    }
                } else {
                    logger.debug("Quest node double-click handler not configured",
                            StructuredLogger.field("questId", quest.id()));
                }
                event.consume();
            }
        });
    }

    private void handleMousePressed(MouseEvent event) {
        if (event.getTarget() instanceof QuestNodeView) {
            return;
        }
        if (event.getButton() == MouseButton.PRIMARY) {
            boolean extend = event.isShiftDown() || event.isControlDown() || event.isMetaDown();
            beginBandSelection(event.getX(), event.getY(), extend);
            event.consume();
            return;
        }
        if (event.getButton() == MouseButton.SECONDARY || event.getButton() == MouseButton.MIDDLE) {
            panning = true;
            panAnchor = new Point2D(event.getX(), event.getY());
            panStartTranslate = new Point2D(translateTransform.getX(), translateTransform.getY());
            event.consume();
        }
    }

    private void handleMouseDragged(MouseEvent event) {
        if (bandSelecting) {
            updateBandSelection(event.getX(), event.getY());
            event.consume();
            return;
        }
        if (!panning) {
            return;
        }
        Point2D current = new Point2D(event.getX(), event.getY());
        Point2D delta = current.subtract(panAnchor);
        translateTransform.setX(panStartTranslate.getX() + delta.getX());
        translateTransform.setY(panStartTranslate.getY() + delta.getY());
        updateMinimap();
        event.consume();
    }

    private void handleMouseReleased(MouseEvent event) {
        if (bandSelecting && event.getButton() == MouseButton.PRIMARY) {
            completeBandSelection();
            event.consume();
            return;
        }
        if (panning && (event.getButton() == MouseButton.SECONDARY || event.getButton() == MouseButton.MIDDLE)) {
            panning = false;
            event.consume();
        }
    }

    private void beginBandSelection(double x, double y, boolean extend) {
        bandSelecting = true;
        bandExtending = extend;
        bandAnchor = new Point2D(x, y);
        selectionRectangle.setLayoutX(x);
        selectionRectangle.setLayoutY(y);
        selectionRectangle.setWidth(0);
        selectionRectangle.setHeight(0);
        selectionRectangle.setVisible(true);
        if (!extend) {
            clearSelection();
        }
    }

    private void updateBandSelection(double x, double y) {
        double minX = Math.min(bandAnchor.getX(), x);
        double minY = Math.min(bandAnchor.getY(), y);
        double width = Math.abs(x - bandAnchor.getX());
        double height = Math.abs(y - bandAnchor.getY());
        selectionRectangle.setLayoutX(minX);
        selectionRectangle.setLayoutY(minY);
        selectionRectangle.setWidth(width);
        selectionRectangle.setHeight(height);
    }

    private void completeBandSelection() {
        bandSelecting = false;
        selectionRectangle.setVisible(false);
        double width = selectionRectangle.getWidth();
        double height = selectionRectangle.getHeight();
        if (width < 2 && height < 2) {
            return;
        }
        double minX = selectionRectangle.getLayoutX();
        double minY = selectionRectangle.getLayoutY();
        for (QuestNodeView node : nodeViews.values()) {
            Bounds sceneBounds = node.localToScene(node.getBoundsInLocal());
            Bounds localBounds = sceneToLocal(sceneBounds);
            if (localBounds != null && localBounds.intersects(minX, minY, width, height)) {
                addToSelection(node, true);
            } else if (!bandExtending && selectedNodes.contains(node)) {
                removeFromSelection(node);
            }
        }
        bandExtending = false;
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
        updateMinimap();
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
            gc.setStroke(edge.isRequired() ? Color.web("#ffcc33") : Color.web("#a366ff"));
            gc.strokeLine(start.getX(), start.getY(), end.getX(), end.getY());
            drawArrow(gc, start, end);
        }
    }

    private void updateMinimap() {
        GraphicsContext gc = minimapLayer.getGraphicsContext2D();
        gc.setTransform(new Affine());
        gc.clearRect(0, 0, MINIMAP_WIDTH, MINIMAP_HEIGHT);

        if (nodeViews.isEmpty()) {
            return;
        }

        Rectangle2D bounds = computeGraphBounds();
        if (bounds.getWidth() <= 0 || bounds.getHeight() <= 0) {
            return;
        }

        double scale = Math.min(MINIMAP_WIDTH / bounds.getWidth(), MINIMAP_HEIGHT / bounds.getHeight());
        double offsetX = (MINIMAP_WIDTH - bounds.getWidth() * scale) / 2.0;
        double offsetY = (MINIMAP_HEIGHT - bounds.getHeight() * scale) / 2.0;

        gc.setFill(Color.color(0.07, 0.07, 0.07, 0.85));
        gc.fillRect(0, 0, MINIMAP_WIDTH, MINIMAP_HEIGHT);

        gc.setLineWidth(1);
        gc.setStroke(Color.color(1, 1, 1, 0.2));
        for (GraphEdge edge : edges) {
            Point2D start = edge.getSource().getCenter();
            Point2D end = edge.getTarget().getCenter();
            double sx = (start.getX() - bounds.getMinX()) * scale + offsetX;
            double sy = (start.getY() - bounds.getMinY()) * scale + offsetY;
            double ex = (end.getX() - bounds.getMinX()) * scale + offsetX;
            double ey = (end.getY() - bounds.getMinY()) * scale + offsetY;
            gc.strokeLine(sx, sy, ex, ey);
        }

        for (QuestNodeView node : nodeViews.values()) {
            double nx = (node.getLayoutX() - bounds.getMinX()) * scale + offsetX;
            double ny = (node.getLayoutY() - bounds.getMinY()) * scale + offsetY;
            double size = Math.max(4, NODE_SIZE * scale * 0.4);
            gc.setFill(selectedNodes.contains(node) ? Color.web("#4a90e2") : Color.web("#d8d8d8"));
            gc.fillOval(nx, ny, size, size);
        }

        Point2D topLeft = toGraphCoordinates(0, 0);
        Point2D bottomRight = toGraphCoordinates(getWidth(), getHeight());
        if (topLeft != null && bottomRight != null) {
            double viewX = (topLeft.getX() - bounds.getMinX()) * scale + offsetX;
            double viewY = (topLeft.getY() - bounds.getMinY()) * scale + offsetY;
            double viewWidth = (bottomRight.getX() - topLeft.getX()) * scale;
            double viewHeight = (bottomRight.getY() - topLeft.getY()) * scale;
            gc.setStroke(Color.web("#f5f5f5"));
            gc.setLineWidth(1.5);
            gc.strokeRect(viewX, viewY, viewWidth, viewHeight);
        }
    }

    private Rectangle2D computeGraphBounds() {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (QuestNodeView node : nodeViews.values()) {
            minX = Math.min(minX, node.getLayoutX());
            minY = Math.min(minY, node.getLayoutY());
            maxX = Math.max(maxX, node.getLayoutX() + NODE_SIZE);
            maxY = Math.max(maxY, node.getLayoutY() + NODE_SIZE);
        }

        if (Double.isInfinite(minX) || Double.isInfinite(minY) || Double.isInfinite(maxX) || Double.isInfinite(maxY)) {
            return new Rectangle2D(0, 0, 1, 1);
        }

        double width = Math.max(1, maxX - minX);
        double height = Math.max(1, maxY - minY);
        return new Rectangle2D(minX, minY, width, height);
    }

    private Point2D toGraphCoordinates(double x, double y) {
        if (getScene() == null) {
            return null;
        }
        Point2D scenePoint = localToScene(x, y);
        if (scenePoint == null) {
            return null;
        }
        return contentGroup.sceneToLocal(scenePoint);
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
        double width = backgroundLayer.getWidth();
        double height = backgroundLayer.getHeight();
        gc.clearRect(0, 0, width, height);

        BackgroundRef background = backgroundRef.get();
        Color fill = fallbackColor(background);
        gc.setFill(fill);
        gc.fillRect(0, 0, width, height);

        Optional<Image> image = loadBackgroundImage(background);
        if (image.isEmpty()) {
            return;
        }

        Image tile = image.get();
        double tileWidth = tile.getWidth();
        double tileHeight = tile.getHeight();
        if (tileWidth <= 0 || tileHeight <= 0) {
            return;
        }

        BackgroundRepeat repeat = background.repeat().orElse(BackgroundRepeat.BOTH);
        if (repeat == BackgroundRepeat.NONE) {
            Point2D offset = alignmentOffset(background.alignment(), width, height, tileWidth, tileHeight);
            gc.drawImage(tile, offset.getX(), offset.getY());
            return;
        }

        boolean repeatX = repeat == BackgroundRepeat.BOTH || repeat == BackgroundRepeat.HORIZONTAL;
        boolean repeatY = repeat == BackgroundRepeat.BOTH || repeat == BackgroundRepeat.VERTICAL;

        Point2D anchor = alignmentOffset(background.alignment(), width, height, tileWidth, tileHeight);
        double startX = repeatX ? repeatStart(anchor.getX(), tileWidth) : anchor.getX();
        double startY = repeatY ? repeatStart(anchor.getY(), tileHeight) : anchor.getY();

        for (double drawX = startX; drawX < width; drawX += tileWidth) {
            if (!repeatX && drawX > startX) {
                break;
            }
            for (double drawY = startY; drawY < height; drawY += tileHeight) {
                if (!repeatY && drawY > startY) {
                    break;
                }
                gc.drawImage(tile, drawX, drawY);
            }
        }
    }

    private Color fallbackColor(BackgroundRef background) {
        if (background == null) {
            return Color.web("#1e1e1e");
        }
        Optional<String> colorHex = background.colorHex();
        if (colorHex.isPresent()) {
            try {
                return Color.web(colorHex.get());
            } catch (IllegalArgumentException ignored) {
                // fall back to derived color
            }
        }
        return colorFromString(background.texture());
    }

    private Optional<Image> loadBackgroundImage(BackgroundRef background) {
        if (background == null) {
            return Optional.empty();
        }
        Optional<Image> fromPath = background.path().flatMap(this::loadBackgroundImageFromPath);
        if (fromPath.isPresent()) {
            return fromPath;
        }
        return loadBackgroundImageFromPath(background.texture());
    }

    private Optional<Image> loadBackgroundImageFromPath(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        if (backgroundImages.containsKey(value)) {
            return Optional.ofNullable(backgroundImages.get(value));
        }
        Optional<Image> loaded = loadImage(value);
        backgroundImages.put(value, loaded.orElse(null));
        return loaded;
    }

    private Optional<Image> loadImage(String value) {
        try {
            Path path = Path.of(value);
            if (!Files.exists(path)) {
                return Optional.empty();
            }
            return Optional.of(new Image(path.toUri().toString(), true));
        } catch (InvalidPathException ex) {
            return Optional.empty();
        }
    }

    private Point2D alignmentOffset(Optional<BackgroundAlignment> alignment,
                                    double canvasWidth,
                                    double canvasHeight,
                                    double tileWidth,
                                    double tileHeight) {
        if (alignment.isEmpty()) {
            return new Point2D(0, 0);
        }
        BackgroundAlignment value = alignment.get();
        double x;
        double y;
        switch (value) {
            case CENTER -> {
                x = (canvasWidth - tileWidth) / 2;
                y = (canvasHeight - tileHeight) / 2;
            }
            case TOP_LEFT -> {
                x = 0;
                y = 0;
            }
            case TOP_RIGHT -> {
                x = canvasWidth - tileWidth;
                y = 0;
            }
            case BOTTOM_LEFT -> {
                x = 0;
                y = canvasHeight - tileHeight;
            }
            case BOTTOM_RIGHT -> {
                x = canvasWidth - tileWidth;
                y = canvasHeight - tileHeight;
            }
            case TOP -> {
                x = (canvasWidth - tileWidth) / 2;
                y = 0;
            }
            case BOTTOM -> {
                x = (canvasWidth - tileWidth) / 2;
                y = canvasHeight - tileHeight;
            }
            case LEFT -> {
                x = 0;
                y = (canvasHeight - tileHeight) / 2;
            }
            case RIGHT -> {
                x = canvasWidth - tileWidth;
                y = (canvasHeight - tileHeight) / 2;
            }
            default -> {
                x = 0;
                y = 0;
            }
        }
        return new Point2D(clamp(x, -tileWidth, canvasWidth), clamp(y, -tileHeight, canvasHeight));
    }

    private double repeatStart(double anchor, double tileSize) {
        if (tileSize <= 0) {
            return 0;
        }
        double start = anchor % tileSize;
        if (start > 0) {
            start -= tileSize;
        }
        if (start == 0) {
            start = -tileSize;
        }
        return start;
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
        private boolean selected;
        private static final PseudoClass SELECTED_PSEUDO_CLASS = PseudoClass.getPseudoClass("selected");

        private QuestNodeView(QuestGraphModel.Node modelNode) {
            this.modelNode = modelNode;
            setPrefSize(NODE_SIZE, NODE_SIZE);
            setMinSize(NODE_SIZE, NODE_SIZE);
            setMaxSize(NODE_SIZE, NODE_SIZE);
            setPickOnBounds(false);
            getStyleClass().add("quest-node");

            ImageView iconView = new ImageView();
            iconView.setFitWidth(NODE_SIZE - 12);
            iconView.setFitHeight(NODE_SIZE - 12);
            iconView.setPreserveRatio(true);

            boolean loadedIcon = false;
            CacheManager cacheManager = UiServiceLocator.cacheManager;
            if (cacheManager != null) {
                String iconId = modelNode.getQuest().icon().icon();
                cacheManager.fetchIcon(iconId).ifPresent(bytes -> iconView.setImage(new Image(new ByteArrayInputStream(bytes))));
                loadedIcon = iconView.getImage() != null;
            }

            StackPane stack;
            if (!loadedIcon) {
                Circle iconCircle = new Circle(NODE_SIZE / 2 - 6);
                iconCircle.setFill(colorFromString(modelNode.getQuest().icon().icon()));
                iconCircle.setStroke(Color.web("#202020"));
                iconCircle.setStrokeWidth(2);
                iconCircle.setMouseTransparent(true);

                Label iconLabel = new Label(shortIconName(modelNode.getQuest()));
                iconLabel.setTextFill(Color.WHITE);
                iconLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
                iconLabel.setMouseTransparent(true);

                stack = new StackPane(iconCircle, iconLabel, badge);
                StackPane.setAlignment(iconCircle, Pos.CENTER);
                StackPane.setAlignment(iconLabel, Pos.CENTER);
            } else {
                stack = new StackPane(iconView, badge);
                StackPane.setAlignment(iconView, Pos.CENTER);
            }

            stack.setPrefSize(NODE_SIZE, NODE_SIZE);
            stack.setMinSize(NODE_SIZE, NODE_SIZE);
            stack.setMaxSize(NODE_SIZE, NODE_SIZE);
            StackPane.setAlignment(badge, Pos.TOP_RIGHT);
            badge.setTranslateX(-10);
            badge.setTranslateY(10);

            badge.setStroke(Color.web("#1f1f1f"));
            badge.setStrokeWidth(2);
            badge.setMouseTransparent(true);

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

        public void setSelected(boolean selected) {
            if (this.selected == selected) {
                return;
            }
            this.selected = selected;
            pseudoClassStateChanged(SELECTED_PSEUDO_CLASS, selected);
        }

        public boolean isSelected() {
            return selected;
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
}