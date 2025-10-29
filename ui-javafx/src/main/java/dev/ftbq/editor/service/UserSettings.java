package dev.ftbq.editor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.ftbq.editor.io.JsonConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Persists lightweight editor settings under the user configuration directory.
 */
public final class UserSettings {

    private static final Path SETTINGS_DIR = Path.of(System.getProperty("user.home"), ".ftbq-editor");
    private static final Path SETTINGS_FILE = SETTINGS_DIR.resolve("settings.json");
    private static final ObjectMapper MAPPER = JsonConfig.OBJECT_MAPPER.copy()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private UserSettings() {
    }

    public static EditorSettings load() {
        if (Files.exists(SETTINGS_FILE)) {
            try {
                return MAPPER.readValue(SETTINGS_FILE.toFile(), EditorSettings.class);
            } catch (IOException ignored) {
                // Fall back to defaults when the file cannot be read.
            }
        }
        return EditorSettings.defaults();
    }

    public static void save(EditorSettings settings) {
        Objects.requireNonNull(settings, "settings");
        try {
            Files.createDirectories(SETTINGS_DIR);
            MAPPER.writeValue(SETTINGS_FILE.toFile(), settings);
        } catch (IOException ignored) {
            // Persist failures should not crash the editor.
        }
    }

    public static Path autosaveDirectory() {
        return SETTINGS_DIR.resolve("autosaves");
    }

    public record EditorSettings(int autosaveIntervalMinutes, boolean createZhTemplateOnGenerate) {

        public static EditorSettings defaults() {
            return new EditorSettings(1, false);
        }

        public EditorSettings {
            if (autosaveIntervalMinutes < 0) {
                autosaveIntervalMinutes = 0;
            }
        }

        public EditorSettings withAutosaveInterval(int minutes) {
            return new EditorSettings(minutes, createZhTemplateOnGenerate);
        }

        public EditorSettings withZhTemplate(boolean enabled) {
            return new EditorSettings(autosaveIntervalMinutes, enabled);
        }
    }
}
