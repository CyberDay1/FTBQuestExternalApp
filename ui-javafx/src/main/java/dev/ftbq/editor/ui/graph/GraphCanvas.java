package dev.ftbq.editor.ui.graph;

import javafx.css.CssMetaData;
import javafx.css.Styleable;
import javafx.css.StyleableObjectProperty;
import javafx.css.StyleableProperty;
import javafx.css.converter.PaintConverter;
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
        getStyleClass().add("graph-canvas");
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
        enableHandlers();
        redraw();
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
            if (e.getButton() == MouseButton.MIDDLE) {
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
        widthProperty().addListener((obs, a, b) -> redraw());
        heightProperty().addListener((obs, a, b) -> redraw());
    }

    private void apply() {
        world.setToTransform(scale, 0, 0, scale, translateX, translateY);
        redraw();
    }

    public void redraw() {
        GraphicsContext g = getGraphicsContext2D();
        g.setTransform(new Affine());
        g.clearRect(0, 0, getWidth(), getHeight());
        drawGrid(g);
        if (onRedraw != null) {
            onRedraw.accept(null);
        }
    }

    private void drawGrid(GraphicsContext g) {
        g.setTransform(world);
        g.setStroke(getGridColor());
        g.setLineWidth(1.0 / scale);
        double step = gridSize;
        double w = getWidth();
        double h = getHeight();
        for (double x = -w; x < w * 2; x += step) {
            g.strokeLine(x, -h, x, h * 2);
        }
        for (double y = -h; y < h * 2; y += step) {
            g.strokeLine(-w, y, w * 2, y);
        }
    }

    public Color getGridColor() {
        return toColor(gridPaint.get(), DEFAULT_GRID_COLOR);
    }

    public Color getEdgeRequiredColor() {
        return toColor(edgeRequiredPaint.get(), DEFAULT_EDGE_REQUIRED_COLOR);
    }

    public Color getEdgeOptionalColor() {
        return toColor(edgeOptionalPaint.get(), DEFAULT_EDGE_OPTIONAL_COLOR);
    }

    public double[] screenToWorld(double sx, double sy) {
        try {
            javafx.geometry.Point2D p = world.inverseTransform(sx, sy);
            return new double[]{p.getX(), p.getY()};
        } catch (NonInvertibleTransformException e) {
            return new double[]{0, 0};
        }
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


