package dev.ftbq.editor.ui.graph;

import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.PaintConverter;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.transform.Affine;
import javafx.scene.transform.NonInvertibleTransformException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class GraphCanvas extends Canvas {
    // Throttling constants
    private static final long MIN_RENDER_INTERVAL_MS = 16; // ~60 FPS
    private static final long MIN_PAN_INTERVAL_MS = 16; // Throttle pan updates
    private static final int MAX_GRID_LINES = 1000; // Prevent excessive grid drawing
    private static final double MAX_PAN_DELTA = 50; // Max pixels per pan update

    private static final Color DEFAULT_GRID_COLOR = Color.web("#d4d9e2");
    private static final Color DEFAULT_EDGE_REQUIRED_COLOR = Color.web("#3564c2");
    private static final Color DEFAULT_EDGE_OPTIONAL_COLOR = Color.web("#7f9dd6");

    private static final CssMetaData<GraphCanvas, Paint> GRID_PAINT_META =
            new CssMetaData<>("-graph-grid-color", PaintConverter.getInstance(), DEFAULT_GRID_COLOR) {
                @Override
                public boolean isSettable(GraphCanvas node) {
                    return node.gridPaint == null || !node.gridPaint.isBound();
                }

                @Override
                public StyleableProperty<Paint> getStyleableProperty(GraphCanvas node) {
                    return node.gridPaint;
                }
            };

    private static final CssMetaData<GraphCanvas, Paint> EDGE_REQUIRED_PAINT_META =
            new CssMetaData<>("-graph-edge-required-color", PaintConverter.getInstance(), DEFAULT_EDGE_REQUIRED_COLOR) {
                @Override
                public boolean isSettable(GraphCanvas node) {
                    return node.edgeRequiredPaint == null || !node.edgeRequiredPaint.isBound();
                }

                @Override
                public StyleableProperty<Paint> getStyleableProperty(GraphCanvas node) {
                    return node.edgeRequiredPaint;
                }
            };

    private static final CssMetaData<GraphCanvas, Paint> EDGE_OPTIONAL_PAINT_META =
            new CssMetaData<>("-graph-edge-optional-color", PaintConverter.getInstance(), DEFAULT_EDGE_OPTIONAL_COLOR) {
                @Override
                public boolean isSettable(GraphCanvas node) {
                    return node.edgeOptionalPaint == null || !node.edgeOptionalPaint.isBound();
                }

                @Override
                public StyleableProperty<Paint> getStyleableProperty(GraphCanvas node) {
                    return node.edgeOptionalPaint;
                }
            };

    private static final List<CssMetaData<? extends Styleable, ?>> CSS_META_DATA;

    static {
        List<CssMetaData<? extends Styleable, ?>> list = new ArrayList<>(Canvas.getClassCssMetaData());
        list.add(GRID_PAINT_META);
        list.add(EDGE_REQUIRED_PAINT_META);
        list.add(EDGE_OPTIONAL_PAINT_META);
        CSS_META_DATA = Collections.unmodifiableList(list);
    }

    private double scale = 1.0;
    private double translateX = 0;
    private double translateY = 0;
    private double gridSize = 8.0;
    private boolean snapToGrid = true;

    private double dragStartX, dragStartY;
    private boolean panning = false;

    private final Affine world = new Affine();
    private Consumer<Void> onRedraw;

    private long lastRenderTime = 0;
    private long lastPanTime = 0;
    private boolean renderingEnabled = true;

    private final StyleableObjectProperty<Paint> gridPaint =
            new StyleableObjectProperty<>(DEFAULT_GRID_COLOR) {
                @Override
                public Object getBean() {
                    return GraphCanvas.this;
                }

                @Override
                public String getName() {
                    return "gridPaint";
                }

                @Override
                public CssMetaData<GraphCanvas, Paint> getCssMetaData() {
                    return GRID_PAINT_META;
                }
            };

    private final StyleableObjectProperty<Paint> edgeRequiredPaint =
            new StyleableObjectProperty<>(DEFAULT_EDGE_REQUIRED_COLOR) {
                @Override
                public Object getBean() {
                    return GraphCanvas.this;
                }

                @Override
                public String getName() {
                    return "edgeRequiredPaint";
                }

                @Override
                public CssMetaData<GraphCanvas, Paint> getCssMetaData() {
                    return EDGE_REQUIRED_PAINT_META;
                }
            };

    private final StyleableObjectProperty<Paint> edgeOptionalPaint =
            new StyleableObjectProperty<>(DEFAULT_EDGE_OPTIONAL_COLOR) {
                @Override
                public Object getBean() {
                    return GraphCanvas.this;
                }

                @Override
                public String getName() {
                    return "edgeOptionalPaint";
                }

                @Override
                public CssMetaData<GraphCanvas, Paint> getCssMetaData() {
                    return EDGE_OPTIONAL_PAINT_META;
                }
            };

    public GraphCanvas(double w, double h) {
        getStyleClass().addAll("graph-canvas", "dark-theme");

        setWidth(w);
        setHeight(h);

        gridPaint.addListener((obs, oldPaint, newPaint) -> redraw());
        edgeRequiredPaint.addListener((obs, oldPaint, newPaint) -> redraw());
        edgeOptionalPaint.addListener((obs, oldPaint, newPaint) -> redraw());

        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                applyCss();
                redraw();
            }
        });

        // Simple listeners - just redraw on significant changes
        widthProperty().addListener((obs, oldVal, newVal) -> {
            if (Math.abs(newVal.doubleValue() - oldVal.doubleValue()) > 1.0) {
                redraw();
            }
        });

        heightProperty().addListener((obs, oldVal, newVal) -> {
            if (Math.abs(newVal.doubleValue() - oldVal.doubleValue()) > 1.0) {
                redraw();
            }
        });

        enableHandlers();
        redraw();
    }

    private boolean shouldThrottleRender() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRenderTime < MIN_RENDER_INTERVAL_MS) {
            return true;
        }
        lastRenderTime = currentTime;
        return false;
    }

    private boolean shouldThrottlePan() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPanTime < MIN_PAN_INTERVAL_MS) {
            return true;
        }
        lastPanTime = currentTime;
        return false;
    }

    public void cleanup() {
        try {
            renderingEnabled = false;
            GraphicsContext gc = getGraphicsContext2D();
            if (gc != null) {
                gc.clearRect(0, 0, getWidth(), getHeight());
            }
            onRedraw = null;
        } catch (Exception e) {
            System.err.println("Error during canvas cleanup: " + e.getMessage());
        }
    }

    public void setOnRedraw(Consumer<Void> cb) {
        this.onRedraw = cb;
    }

    public Affine getWorld() {
        return world;
    }

    public double getScale() {
        return scale;
    }

    public double getGridSize() {
        return gridSize;
    }

    public void setGridSize(double gridSize) {
        if (!Double.isFinite(gridSize) || gridSize <= 0) {
            throw new IllegalArgumentException("gridSize must be positive and finite");
        }
        if (Math.abs(this.gridSize - gridSize) < 1e-9) {
            return;
        }
        this.gridSize = gridSize;
        redraw();
    }

    public boolean isSnapToGrid() {
        return snapToGrid;
    }

    public void setSnapToGrid(boolean snapToGrid) {
        this.snapToGrid = snapToGrid;
    }

    public double getPanX() {
        return translateX;
    }

    public double getPanY() {
        return translateY;
    }

    public void zoom(double factor, double pivotX, double pivotY) {
        // Zoom disabled to prevent crashes
    }

    public void pan(double dx, double dy) {
        // Throttle pan updates
        if (shouldThrottlePan()) {
            return;
        }

        // Clamp pan delta to prevent huge jumps
        dx = Math.max(-MAX_PAN_DELTA, Math.min(MAX_PAN_DELTA, dx));
        dy = Math.max(-MAX_PAN_DELTA, Math.min(MAX_PAN_DELTA, dy));

        translateX += dx;
        translateY += dy;
        apply();
    }

    public void setState(double scale, double translateX, double translateY) {
        this.scale = 1.0; // Fixed scale
        this.translateX = translateX;
        this.translateY = translateY;
        apply();
    }

    public void resetView() {
        setState(1.0, 0.0, 0.0);
    }

    public double snap(double v) {
        return snapToGrid ? Math.round(v / gridSize) * gridSize : v;
    }

    private void enableHandlers() {
        setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.MIDDLE ||
                    (e.getButton() == MouseButton.PRIMARY && e.isShiftDown())) {
                panning = true;
                dragStartX = e.getX();
                dragStartY = e.getY();
                e.consume();
            }
        });

        setOnMouseDragged(e -> {
            if (panning) {
                pan(e.getX() - dragStartX, e.getY() - dragStartY);
                dragStartX = e.getX();
                dragStartY = e.getY();
                e.consume();
            }
        });

        setOnMouseReleased(e -> {
            if (panning) {
                panning = false;
                e.consume();
            }
        });
    }

    private void apply() {
        world.setToTransform(scale, 0, 0, scale, translateX, translateY);
        redraw();
    }

    public void redraw() {
        if (!renderingEnabled) {
            return;
        }

        try {
            if (shouldThrottleRender()) {
                return;
            }

            double width = getWidth();
            double height = getHeight();

            if (width <= 0 || height <= 0) {
                return;
            }

            GraphicsContext g = getGraphicsContext2D();
            if (g == null) {
                return;
            }

            g.setTransform(new Affine());
            g.clearRect(0, 0, width, height);
            drawGrid(g);

            if (onRedraw != null) {
                onRedraw.accept(null);
            }

        } catch (Exception e) {
            System.err.println("Error during canvas render: " + e.getMessage());

            try {
                GraphicsContext gc = getGraphicsContext2D();
                if (gc != null) {
                    gc.setTransform(new Affine());
                    gc.clearRect(0, 0, getWidth(), getHeight());
                }
            } catch (Exception recoveryError) {
                renderingEnabled = false;
            }
        }
    }

    private void drawGrid(GraphicsContext g) {
        try {
            g.save();
            g.setTransform(world);
            g.setStroke(getGridColor());
            g.setLineWidth(1.0);

            double step = gridSize;
            if (step <= 0 || !Double.isFinite(step)) {
                g.restore();
                return;
            }

            double w = getWidth();
            double h = getHeight();

            double[] topLeft = screenToWorld(0, 0);
            double[] bottomRight = screenToWorld(w, h);

            // Add padding to prevent edge artifacts
            double padding = step * 2;
            double startX = Math.floor((topLeft[0] - padding) / step) * step;
            double startY = Math.floor((topLeft[1] - padding) / step) * step;
            double endX = Math.ceil((bottomRight[0] + padding) / step) * step;
            double endY = Math.ceil((bottomRight[1] + padding) / step) * step;

            if (!Double.isFinite(startX) || !Double.isFinite(startY) ||
                    !Double.isFinite(endX) || !Double.isFinite(endY)) {
                g.restore();
                return;
            }

            int verticalLines = (int)((endX - startX) / step);
            int horizontalLines = (int)((endY - startY) / step);

            if (verticalLines > MAX_GRID_LINES || horizontalLines > MAX_GRID_LINES) {
                g.restore();
                return;
            }

            // Use path for better performance and less tearing
            g.beginPath();
            for (double x = startX; x <= endX; x += step) {
                g.moveTo(x, startY);
                g.lineTo(x, endY);
            }
            for (double y = startY; y <= endY; y += step) {
                g.moveTo(startX, y);
                g.lineTo(endX, y);
            }
            g.stroke();

            g.restore();

        } catch (Exception e) {
            System.err.println("Error drawing grid: " + e.getMessage());
        }
    }

    public Paint getGridPaint() {
        return gridPaint.get();
    }

    public void setGridPaint(Paint paint) {
        gridPaint.set(paint);
    }

    public StyleableObjectProperty<Paint> gridPaintProperty() {
        return gridPaint;
    }

    public Color getGridColor() {
        return toColor(getGridPaint(), DEFAULT_GRID_COLOR);
    }

    public Paint getEdgeRequiredPaint() {
        return edgeRequiredPaint.get();
    }

    public void setEdgeRequiredPaint(Paint paint) {
        edgeRequiredPaint.set(paint);
    }

    public StyleableObjectProperty<Paint> edgeRequiredPaintProperty() {
        return edgeRequiredPaint;
    }

    public Color getEdgeRequiredColor() {
        return toColor(getEdgeRequiredPaint(), DEFAULT_EDGE_REQUIRED_COLOR);
    }

    public Paint getEdgeOptionalPaint() {
        return edgeOptionalPaint.get();
    }

    public void setEdgeOptionalPaint(Paint paint) {
        edgeOptionalPaint.set(paint);
    }

    public StyleableObjectProperty<Paint> edgeOptionalPaintProperty() {
        return edgeOptionalPaint;
    }

    public Color getEdgeOptionalColor() {
        return toColor(getEdgeOptionalPaint(), DEFAULT_EDGE_OPTIONAL_COLOR);
    }

    public double[] screenToWorld(double sx, double sy) {
        Point2D local = new Point2D(sx, sy);
        if (getScene() != null) {
            boolean converted = false;
            try {
                Point2D scenePoint = getScene().getRoot().screenToLocal(sx, sy);
                Point2D candidate = sceneToLocal(scenePoint);
                if (!Double.isNaN(candidate.getX()) && !Double.isNaN(candidate.getY())) {
                    local = candidate;
                    converted = true;
                }
            } catch (RuntimeException ignored) {
            }
            if (!converted) {
                try {
                    Point2D candidate = sceneToLocal(sx, sy, true);
                    if (!Double.isNaN(candidate.getX()) && !Double.isNaN(candidate.getY())) {
                        local = candidate;
                    }
                } catch (RuntimeException ignored) {
                }
            }
        }
        try {
            Point2D p = world.inverseTransform(local);
            return new double[]{p.getX(), p.getY()};
        } catch (NonInvertibleTransformException e) {
            return new double[]{0, 0};
        }
    }

    public double[] screenToWorldDelta(double sx, double sy, double sx2, double sy2) {
        double[] start = screenToWorld(sx, sy);
        double[] end = screenToWorld(sx2, sy2);
        return new double[]{end[0] - start[0], end[1] - start[1]};
    }

    public double[] worldToScreen(double wx, double wy) {
        Point2D p = world.transform(wx, wy);
        return new double[]{p.getX(), p.getY()};
    }

    public static List<CssMetaData<? extends Styleable, ?>> getClassCssMetaData() {
        return CSS_META_DATA;
    }

    @Override
    public List<CssMetaData<? extends Styleable, ?>> getCssMetaData() {
        return getClassCssMetaData();
    }

    private Color toColor(Paint paint, Color fallback) {
        if (paint instanceof Color color) {
            return color;
        }
        return fallback;
    }
}