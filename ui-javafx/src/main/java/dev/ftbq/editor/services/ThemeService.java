package dev.ftbq.editor.services;

import javafx.scene.Scene;

import java.util.Map;

public final class ThemeService {
    private static final Map<String, String> THEMES = Map.of(
            "light", "/dev/ftbq/editor/theme/light.css",
            "dark", "/dev/ftbq/editor/theme/dark.css",
            "high_contrast", "/dev/ftbq/editor/theme/high_contrast.css");

    private ThemeService() {
    }

    public static void apply(Scene scene, String theme) {
        scene.getStylesheets().clear();
        String path = THEMES.getOrDefault(theme, "/dev/ftbq/editor/theme/light.css");
        scene.getStylesheets().add(ThemeService.class.getResource(path).toExternalForm());
    }
}
