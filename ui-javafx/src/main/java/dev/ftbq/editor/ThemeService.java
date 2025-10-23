package dev.ftbq.editor;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.prefs.Preferences;

public final class ThemeService {
    private static final String PREFERENCE_KEY_THEME = "ui.theme";
    private static final KeyCodeCombination LIGHT_COMBINATION = new KeyCodeCombination(
            KeyCode.L, KeyCombination.CONTROL_DOWN, KeyCombination.ALT_DOWN);
    private static final KeyCodeCombination DARK_COMBINATION = new KeyCodeCombination(
            KeyCode.D, KeyCombination.CONTROL_DOWN, KeyCombination.ALT_DOWN);
    private static final KeyCodeCombination HIGH_CONTRAST_COMBINATION = new KeyCodeCombination(
            KeyCode.H, KeyCombination.CONTROL_DOWN, KeyCombination.ALT_DOWN);

    private final Map<Theme, String> stylesheetUrls;
    private final Set<String> allStylesheetUrls;
    private final Map<Stage, ChangeListener<Scene>> stageListeners = new WeakHashMap<>();
    private final Preferences preferences;
    private final ObjectProperty<Theme> currentTheme;

    private ThemeService() {
        this.preferences = Preferences.userNodeForPackage(ThemeService.class);
        this.stylesheetUrls = Collections.unmodifiableMap(loadStylesheetUrls());
        this.allStylesheetUrls = Collections.unmodifiableSet(new HashSet<>(stylesheetUrls.values()));
        Theme savedTheme = Theme.fromId(preferences.get(PREFERENCE_KEY_THEME, Theme.LIGHT.getId()));
        this.currentTheme = new SimpleObjectProperty<>(savedTheme);
        this.currentTheme.addListener((obs, oldTheme, newTheme) -> {
            if (newTheme == null || newTheme == oldTheme) {
                return;
            }
            preferences.put(PREFERENCE_KEY_THEME, newTheme.getId());
            applyThemeToAll();
        });
    }

    public static ThemeService getInstance() {
        return Holder.INSTANCE;
    }

    public Theme getTheme() {
        return currentTheme.get();
    }

    public void setTheme(Theme theme) {
        if (theme != null && theme != currentTheme.get()) {
            currentTheme.set(theme);
        }
    }

    public void registerStage(Stage stage) {
        Objects.requireNonNull(stage, "stage");

        ChangeListener<Scene> existing = stageListeners.remove(stage);
        if (existing != null) {
            stage.sceneProperty().removeListener(existing);
        }

        ChangeListener<Scene> listener = (obs, oldScene, newScene) -> {
            if (oldScene != null) {
                removeAccelerators(oldScene);
                clearThemeStylesheet(oldScene);
            }
            if (newScene != null) {
                configureScene(newScene);
            }
        };
        stage.sceneProperty().addListener(listener);
        stageListeners.put(stage, listener);

        Scene scene = stage.getScene();
        if (scene != null) {
            configureScene(scene);
        }

        if (!Boolean.TRUE.equals(stage.getProperties().get(ThemeService.class))) {
            stage.addEventHandler(WindowEvent.WINDOW_HIDDEN, event -> unregisterStage(stage));
            stage.getProperties().put(ThemeService.class, Boolean.TRUE);
        }
    }

    private void unregisterStage(Stage stage) {
        ChangeListener<Scene> listener = stageListeners.remove(stage);
        if (listener != null) {
            stage.sceneProperty().removeListener(listener);
        }
        Scene scene = stage.getScene();
        if (scene != null) {
            removeAccelerators(scene);
            clearThemeStylesheet(scene);
        }
    }

    private void configureScene(Scene scene) {
        installAccelerators(scene);
        applyStylesheet(scene, getTheme());
    }

    private void installAccelerators(Scene scene) {
        scene.getAccelerators().put(LIGHT_COMBINATION, () -> setTheme(Theme.LIGHT));
        scene.getAccelerators().put(DARK_COMBINATION, () -> setTheme(Theme.DARK));
        scene.getAccelerators().put(HIGH_CONTRAST_COMBINATION, () -> setTheme(Theme.HIGH_CONTRAST));
    }

    private void removeAccelerators(Scene scene) {
        scene.getAccelerators().remove(LIGHT_COMBINATION);
        scene.getAccelerators().remove(DARK_COMBINATION);
        scene.getAccelerators().remove(HIGH_CONTRAST_COMBINATION);
    }

    private void applyStylesheet(Scene scene, Theme theme) {
        clearThemeStylesheet(scene);
        String stylesheet = stylesheetUrls.get(theme);
        if (stylesheet != null && !scene.getStylesheets().contains(stylesheet)) {
            scene.getStylesheets().add(stylesheet);
        }
    }

    private void clearThemeStylesheet(Scene scene) {
        scene.getStylesheets().removeIf(allStylesheetUrls::contains);
    }

    private void applyThemeToAll() {
        ArrayList<Stage> stages = new ArrayList<>(stageListeners.keySet());
        for (Stage stage : stages) {
            if (stage == null) {
                continue;
            }
            Scene scene = stage.getScene();
            if (scene != null) {
                configureScene(scene);
            }
        }
    }

    private Map<Theme, String> loadStylesheetUrls() {
        EnumMap<Theme, String> map = new EnumMap<>(Theme.class);
        for (Theme theme : Theme.values()) {
            URL url = ThemeService.class.getResource("/dev/ftbq/editor/theme/" + theme.getStylesheet());
            if (url == null) {
                throw new IllegalStateException("Missing stylesheet for theme: " + theme);
            }
            map.put(theme, url.toExternalForm());
        }
        return map;
    }

    private static final class Holder {
        private static final ThemeService INSTANCE = new ThemeService();
    }

    public enum Theme {
        LIGHT("light", "light.css"),
        DARK("dark", "dark.css"),
        HIGH_CONTRAST("high_contrast", "high_contrast.css");

        private final String id;
        private final String stylesheet;

        Theme(String id, String stylesheet) {
            this.id = id;
            this.stylesheet = stylesheet;
        }

        public String getId() {
            return id;
        }

        public String getStylesheet() {
            return stylesheet;
        }

        static Theme fromId(String id) {
            if (id != null) {
                for (Theme theme : values()) {
                    if (theme.id.equalsIgnoreCase(id)) {
                        return theme;
                    }
                }
            }
            return LIGHT;
        }
    }
}
