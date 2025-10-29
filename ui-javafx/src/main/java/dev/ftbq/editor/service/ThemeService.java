package dev.ftbq.editor.service;

import javafx.scene.Scene;

public final class ThemeService {
    private static final String DARK = "/dev/ftbq/editor/css/theme-dark.css";
    private static final String LIGHT = "/dev/ftbq/editor/css/theme-light.css";
    private static final String CANVAS = "/dev/ftbq/editor/css/quest-canvas.css";

    public static void apply(Scene scene, boolean dark) {
        scene.getStylesheets().clear();
        scene.getStylesheets().add(CANVAS);
        scene.getStylesheets().add(dark ? DARK : LIGHT);
    }
}
