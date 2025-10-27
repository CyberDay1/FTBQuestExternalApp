package dev.ftbq.editor.ui.graph;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.paint.Color;
import javafx.scene.transform.Affine;
import javafx.scene.transform.NonInvertibleTransformException;

import java.util.function.Consumer;

public class GraphCanvas extends Canvas {
    private double scale = 1.0;
    private double translateX = 0;
    private double translateY = 0;
    private double gridSize = 8.0;
    private boolean snapToGrid = true;

    private double dragStartX, dragStartY;
    private boolean panning = false;

    private final Affine world = new Affine();
    private Consumer<Void> onRedraw;

    public GraphCanvas(double w, double h) {
        setWidth(w);
        setHeight(h);
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
        g.setStroke(Color.web("#3a3a3a"));
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

    public double[] screenToWorld(double sx, double sy) {
        try {
            javafx.geometry.Point2D p = world.inverseTransform(sx, sy);
            return new double[]{p.getX(), p.getY()};
        } catch (NonInvertibleTransformException e) {
            return new double[]{0, 0};
        }
    }
}
