package dev.ftbq.editor.services;

import javafx.scene.Scene;

import java.util.Map;

public final class ThemeService {
    private static final Map<String, String> THEMES = Map.of(
            "light", "/css/light.css",
            "dark", "/css/dark.css",
            "high_contrast", "/css/high_contrast.css");

    private ThemeService() {
    }

    public static void apply(Scene scene, String theme) {
        scene.getStylesheets().clear();
        String path = THEMES.getOrDefault(theme, "/css/light.css");
        scene.getStylesheets().add(ThemeService.class.getResource(path).toExternalForm());
    }
}
