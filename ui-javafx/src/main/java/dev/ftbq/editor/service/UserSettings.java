package dev.ftbq.editor.service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class UserSettings {
    public static final class EditorSettings implements Serializable {
        public boolean showGrid = true;
        public boolean darkTheme = true;
        public boolean smoothPanning = true;
        public int autosaveMinutes = 1;
    }

    private static final String DIR = System.getProperty("user.home") + File.separator + ".ftbq-editor";
    private static final String FILE = DIR + File.separator + "settings.json";

    private static EditorSettings cached;

    public static synchronized EditorSettings get() {
        if (cached != null) return cached;
        Path p = Path.of(FILE);
        try {
            if (Files.exists(p)) {
                String json = Files.readString(p, StandardCharsets.UTF_8).trim();
                EditorSettings es = parse(json);
                cached = es != null ? es : new EditorSettings();
            } else {
                ensureDir();
                cached = new EditorSettings();
                save(cached);
            }
        } catch (Exception e) {
            cached = new EditorSettings();
        }
        return cached;
    }

    public static synchronized void save(EditorSettings es) {
        Objects.requireNonNull(es);
        cached = es;
        ensureDir();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(FILE), StandardCharsets.UTF_8)) {
            w.write(toJson(es));
        } catch (IOException ignored) {}
    }

    private static void ensureDir() {
        new File(DIR).mkdirs();
    }

    public static Path autosaveDirectory() {
        ensureDir();
        Path dir = Path.of(DIR, "autosaves");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {}
        return dir;
    }

    private static String toJson(EditorSettings es) {
        return "{" +
            "\"showGrid\":" + es.showGrid + "," +
            "\"darkTheme\":" + es.darkTheme + "," +
            "\"smoothPanning\":" + es.smoothPanning + "," +
            "\"autosaveMinutes\":" + es.autosaveMinutes
            + "}";
    }

    private static EditorSettings parse(String json) {
        EditorSettings es = new EditorSettings();
        // naive parse to avoid deps
        es.showGrid = json.contains("\"showGrid\":true");
        es.darkTheme = json.contains("\"darkTheme\":true");
        es.smoothPanning = json.contains("\"smoothPanning\":true");
        es.autosaveMinutes = 1;
        try {
            int i = json.indexOf("\"autosaveMinutes\":");
            if (i >= 0) {
                String tail = json.substring(i + 18).replaceAll("[^0-9]", " ").trim();
                String num = tail.split("\\s+")[0];
                es.autosaveMinutes = Math.max(0, Integer.parseInt(num));
            }
        } catch (Exception ignored) {}
        return es;
    }
}
