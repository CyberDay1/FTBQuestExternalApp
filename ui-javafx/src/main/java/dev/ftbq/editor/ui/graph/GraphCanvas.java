package dev.ftbq.editor.ui.graph;

import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.PaintConverter;
import javafx.geometry.Point2D;
import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.transform.Affine;
import javafx.scene.transform.NonInvertibleTransformException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class GraphCanvas extends Canvas {
    // ========== SAFETY CONSTANTS (NEW) ==========
    private static final double MAX_CANVAS_DIMENSION = 8192; // Safe limit for most GPUs
    private static final double MIN_CANVAS_DIMENSION = 1;
    private static final long MIN_RENDER_INTERVAL_MS = 16; // ~60 FPS throttling

    // ========== ORIGINAL CONSTANTS ==========
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

    // NEW: Render safety tracking
    private long lastRenderTime = 0;
    private Rectangle2D viewport;

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

        // NEW: Clamp initial dimensions
        w = Math.max(MIN_CANVAS_DIMENSION, Math.min(MAX_CANVAS_DIMENSION, w));
        h = Math.max(MIN_CANVAS_DIMENSION, Math.min(MAX_CANVAS_DIMENSION, h));

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

        // NEW: Add dimension validation listeners
        widthProperty().addListener((obs, oldVal, newVal) -> {
            double width = newVal.doubleValue();
            if (width > MAX_CANVAS_DIMENSION) {
                System.err.println("Canvas width exceeds maximum (" + MAX_CANVAS_DIMENSION + "), clamping");
                setWidth(MAX_CANVAS_DIMENSION);
            } else if (width < MIN_CANVAS_DIMENSION && width > 0) {
                setWidth(MIN_CANVAS_DIMENSION);
            } else {
                redraw();
            }
        });

        heightProperty().addListener((obs, oldVal, newVal) -> {
            double height = newVal.doubleValue();
            if (height > MAX_CANVAS_DIMENSION) {
                System.err.println("Canvas height exceeds maximum (" + MAX_CANVAS_DIMENSION + "), clamping");
                setHeight(MAX_CANVAS_DIMENSION);
            } else if (height < MIN_CANVAS_DIMENSION && height > 0) {
                setHeight(MIN_CANVAS_DIMENSION);
            } else {
                redraw();
            }
        });

        enableHandlers();
        redraw();
    }

    // ========== NEW: SAFETY METHODS ==========

    /**
     * Checks if the canvas dimensions are safe for rendering.
     */
    private boolean isRenderSafe() {
        double w = getWidth();
        double h = getHeight();

        if (w <= 0 || h <= 0 || Double.isNaN(w) || Double.isNaN(h) ||
                Double.isInfinite(w) || Double.isInfinite(h)) {
            return false;
        }

        if (w > MAX_CANVAS_DIMENSION || h > MAX_CANVAS_DIMENSION) {
            return false;
        }

        double totalPixels = w * h;
        return totalPixels <= (MAX_CANVAS_DIMENSION * MAX_CANVAS_DIMENSION);
    }

    /**
     * Throttle rendering to prevent excessive redraws.
     */
    private boolean shouldThrottleRender() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRenderTime < MIN_RENDER_INTERVAL_MS) {
            return true;
        }
        lastRenderTime = currentTime;
        return false;
    }

    /**
     * Cleanup method to prevent memory leaks.
     */
    public void cleanup() {
        try {
            GraphicsContext gc = getGraphicsContext2D();
            if (gc != null) {
                gc.clearRect(0, 0, getWidth(), getHeight());
            }
            viewport = null;
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
        double oldScale = scale;
        scale = Math.max(0.25, Math.min(4.0, scale * factor));
        double f = scale / oldScale;
        translateX = pivotX - f * (pivotX - translateX);
        translateY = pivotY - f * (pivotY - translateY);
        apply();
    }

    public void pan(double dx, double dy) {
        translateX += dx;
        translateY += dy;
        apply();
    }

    public void setState(double scale, double translateX, double translateY) {
        this.scale = Math.max(0.25, Math.min(4.0, scale));
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
        setOnScroll((ScrollEvent e) -> {
            double f = e.getDeltaY() > 0 ? 1.1 : 0.9;
            zoom(f, e.getX(), e.getY());
            e.consume();
        });
        setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.MIDDLE || (e.getButton() == MouseButton.PRIMARY && e.isShiftDown())) {
                panning = true;
                dragStartX = e.getX();
                dragStartY = e.getY();
            }
        });
        setOnMouseDragged(e -> {
            if (panning) {
                pan(e.getX() - dragStartX, e.getY() - dragStartY);
                dragStartX = e.getX();
                dragStartY = e.getY();
            }
        });
        setOnMouseReleased(e -> panning = false);
    }

    private void apply() {
        world.setToTransform(scale, 0, 0, scale, translateX, translateY);
        redraw();
    }

    // ========== ENHANCED REDRAW METHOD ==========
    public void redraw() {
        try {
            // NEW: Safety checks
            if (!isRenderSafe()) {
                System.err.println("Skipping render - canvas dimensions unsafe: " +
                        getWidth() + "x" + getHeight());
                return;
            }

            // NEW: Throttle excessive redraws
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
                System.err.println("GraphicsContext is null, cannot render");
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
            e.printStackTrace();

            // Attempt recovery
            try {
                GraphicsContext gc = getGraphicsContext2D();
                if (gc != null) {
                    gc.clearRect(0, 0, getWidth(), getHeight());
                }
            } catch (Exception recoveryError) {
                System.err.println("Failed to recover from render error");
            }
        }
    }

    // ========== ENHANCED GRID DRAWING ==========
    private void drawGrid(GraphicsContext g) {
        try {
            g.setTransform(world);
            g.setStroke(getGridColor());
            g.setLineWidth(1.0 / scale);

            double step = gridSize;
            double w = getWidth();
            double h = getHeight();

            // NEW: Calculate visible bounds more conservatively
            double visibleWidth = w / scale;
            double visibleHeight = h / scale;

            // NEW: Limit grid drawing to reasonable bounds
            double maxGridExtent = Math.max(visibleWidth, visibleHeight) * 2;
            maxGridExtent = Math.min(maxGridExtent, MAX_CANVAS_DIMENSION);

            double startX = Math.floor(-translateX / scale / step) * step;
            double startY = Math.floor(-translateY / scale / step) * step;
            double endX = startX + maxGridExtent;
            double endY = startY + maxGridExtent;

            // NEW: Safety check for grid bounds
            if (!Double.isFinite(startX) || !Double.isFinite(startY) ||
                    !Double.isFinite(endX) || !Double.isFinite(endY)) {
                System.err.println("Invalid grid bounds, skipping grid render");
                return;
            }

            // Draw vertical lines
            int lineCount = 0;
            for (double x = startX; x <= endX && lineCount < 10000; x += step) {
                g.strokeLine(x, startY, x, endY);
                lineCount++;
            }

            // Draw horizontal lines
            lineCount = 0;
            for (double y = startY; y <= endY && lineCount < 10000; y += step) {
                g.strokeLine(startX, y, endX, y);
                lineCount++;
            }

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
                // fall back to other coordinate conversions
            }
            if (!converted) {
                try {
                    Point2D candidate = sceneToLocal(sx, sy, true);
                    if (!Double.isNaN(candidate.getX()) && !Double.isNaN(candidate.getY())) {
                        local = candidate;
                    }
                } catch (RuntimeException ignored) {
                    // use original coordinates when conversion fails
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

    // ========== RESIZE OVERRIDE (NEW) ==========
    @Override
    public void resize(double width, double height) {
        width = Math.max(MIN_CANVAS_DIMENSION, Math.min(MAX_CANVAS_DIMENSION, width));
        height = Math.max(MIN_CANVAS_DIMENSION, Math.min(MAX_CANVAS_DIMENSION, height));
        super.resize(width, height);
    }

    // ========== DIAGNOSTIC METHOD (NEW) ==========
    public void printDiagnostics() {
        System.out.println("=== GraphCanvas Diagnostics ===");
        System.out.println("Canvas dimensions: " + getWidth() + "x" + getHeight());
        System.out.println("Scale: " + scale);
        System.out.println("Translation: (" + translateX + ", " + translateY + ")");
        System.out.println("Grid size: " + gridSize);
        System.out.println("Render safe: " + isRenderSafe());
        System.out.println("==============================");
    }
}