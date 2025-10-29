package dev.ftbq.editor.ui.graph;

import dev.ftbq.editor.service.UserSettings;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.VPos;
import javafx.scene.CacheHint;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

public class QuestCanvas extends Region {
    private final Canvas canvas = new Canvas();
    private final BooleanProperty showGrid = new SimpleBooleanProperty(UserSettings.get().showGrid);
    private final BooleanProperty smoothPanning = new SimpleBooleanProperty(UserSettings.get().smoothPanning);
    private double panX = 0, panY = 0;
    private double targetPanX = 0, targetPanY = 0;
    private final AnimationTimer panTimer;

    public QuestCanvas() {
        getChildren().add(canvas);
        setMinSize(0,0);
        canvas.setCache(true);
        canvas.setCacheHint(CacheHint.SPEED);
        panTimer = new AnimationTimer() {
            @Override public void handle(long now) {
                if (!smoothPanning.get()) {
                    panX = targetPanX;
                    panY = targetPanY;
                    redraw();
                    stop();
                    return;
                }
                double lerp = 0.18;
                double nx = panX + (targetPanX - panX) * lerp;
                double ny = panY + (targetPanY - panY) * lerp;
                if (Math.hypot(nx - panX, ny - panY) < 0.1) {
                    panX = targetPanX; panY = targetPanY;
                    redraw();
                    stop();
                } else {
                    panX = nx; panY = ny;
                    redraw();
                }
            }
        };
        widthProperty().addListener((obs,o,n)-> resizeCanvas());
        heightProperty().addListener((obs,o,n)-> resizeCanvas());
        // initial draw
        Platform.runLater(this::redraw);
        getStyleClass().add("quest-canvas-root");
    }

    private void resizeCanvas() {
        canvas.setWidth(Math.max(1,getWidth()));
        canvas.setHeight(Math.max(1,getHeight()));
        redraw();
    }

    public void setShowGrid(boolean v) {
        showGrid.set(v);
        UserSettings.EditorSettings es = UserSettings.get();
        es.showGrid = v;
        UserSettings.save(es);
        redraw();
    }

    public void setSmoothPanning(boolean v) {
        smoothPanning.set(v);
        UserSettings.EditorSettings es = UserSettings.get();
        es.smoothPanning = v;
        UserSettings.save(es);
    }

    public void panTo(double x, double y) {
        // clamp to reasonable bounds (no zoom, fixed scale)
        targetPanX = clamp(x, -10000, 10000);
        targetPanY = clamp(y, -10000, 10000);
        panTimer.stop();
        if (smoothPanning.get()) {
            panTimer.start();
        } else {
            panX = targetPanX;
            panY = targetPanY;
            redraw();
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    public void redraw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        // background
        g.setFill(getBackgroundFill());
        g.fillRect(0,0,w,h);

        if (showGrid.get()) {
            drawGrid(g, w, h);
        }

        // (Quest nodes rendering is elsewhere; this class provides grid + panning backdrop)
        // draw origin crosshair subtle (optional)
        g.setStroke(getGridStroke().deriveColor(0,1,1,0.5));
        g.setLineWidth(1);
        g.strokeLine(-panX-6, -panY, -panX+6, -panY);
        g.strokeLine(-panX, -panY-6, -panX, -panY+6);
    }

    private void drawGrid(GraphicsContext g, double w, double h) {
        final int spacing = 32;
        g.setStroke(getGridStroke());
        g.setLineWidth(1);
        g.setTextBaseline(VPos.TOP);

        double offsetX = (panX % spacing + spacing) % spacing;
        double offsetY = (panY % spacing + spacing) % spacing;

        for (double x = -offsetX; x < w; x += spacing) {
            g.strokeLine(x, 0, x, h);
        }
        for (double y = -offsetY; y < h; y += spacing) {
            g.strokeLine(0, y, w, y);
        }
    }

    private Color getBackgroundFill() {
        // CSS exposes -fx-quest-bg; fall back by checking styleclass via lookup not accessible here.
        // Simple heuristic: read a pseudo flag from style via getStyle() is brittle; choose neutral then CSS can overlay.
        return Color.web("#1b1d22"); // dark default; CSS can override via parent containers if needed
    }

    private Color getGridStroke() {
        // theme-aware by CSS variable? JavaFX Canvas doesn't read CSS vars; choose neutral that works with dark/light.
        // We'll set different stroke via CSS using a snapshot pattern later if needed; here: slightly visible.
        return Color.web("#ffffff", 0.08);
    }
}
