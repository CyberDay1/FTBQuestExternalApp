package dev.ftbq.editor;

import java.util.Objects;
import java.util.prefs.Preferences;

import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public final class WindowSettings {
    private static final String KEY_WIDTH = "window.width";
    private static final String KEY_HEIGHT = "window.height";
    private static final String KEY_X = "window.x";
    private static final String KEY_Y = "window.y";
    private static final double DEFAULT_WIDTH = 1280.0;
    private static final double DEFAULT_HEIGHT = 800.0;

    private final Preferences preferences;

    public WindowSettings() {
        this.preferences = Preferences.userNodeForPackage(WindowSettings.class);
    }

    public void apply(Stage stage) {
        stage.setWidth(preferences.getDouble(KEY_WIDTH, DEFAULT_WIDTH));
        stage.setHeight(preferences.getDouble(KEY_HEIGHT, DEFAULT_HEIGHT));

        double x = preferences.getDouble(KEY_X, Double.NaN);
        double y = preferences.getDouble(KEY_Y, Double.NaN);
        if (Double.isFinite(x) && Double.isFinite(y)) {
            stage.setX(x);
            stage.setY(y);
        }
    }

    public void observe(Stage stage) {
        Objects.requireNonNull(stage, "stage");

        stage.widthProperty().addListener((obs, oldValue, newValue) -> storeSize(KEY_WIDTH, newValue));
        stage.heightProperty().addListener((obs, oldValue, newValue) -> storeSize(KEY_HEIGHT, newValue));
        stage.xProperty().addListener((obs, oldValue, newValue) -> storeCoordinate(KEY_X, newValue));
        stage.yProperty().addListener((obs, oldValue, newValue) -> storeCoordinate(KEY_Y, newValue));

        stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> save(stage));
    }

    private void storeSize(String key, Number value) {
        double candidate = value.doubleValue();
        if (Double.isFinite(candidate) && candidate > 0) {
            preferences.putDouble(key, candidate);
        }
    }

    private void storeCoordinate(String key, Number value) {
        double candidate = value.doubleValue();
        if (Double.isFinite(candidate)) {
            preferences.putDouble(key, candidate);
        }
    }

    private void save(Stage stage) {
        storeSize(KEY_WIDTH, stage.getWidth());
        storeSize(KEY_HEIGHT, stage.getHeight());
        storeCoordinate(KEY_X, stage.getX());
        storeCoordinate(KEY_Y, stage.getY());
    }
}


