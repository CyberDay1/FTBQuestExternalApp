package dev.ftbq.editor.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.io.JsonConfig;
import dev.ftbq.editor.services.logging.StructuredLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import dev.ftbq.editor.domain.HexId;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Periodically writes an autosave snapshot for the current quest file.
 */
public final class AutosaveService {

    private static final ObjectMapper MAPPER = JsonConfig.OBJECT_MAPPER.copy();

    private final Supplier<QuestFile> questSupplier;
    private final Supplier<String> projectNameSupplier;
    private final StructuredLogger logger;
    private ScheduledExecutorService executor;
    private volatile int intervalMinutes;

    public AutosaveService(Supplier<QuestFile> questSupplier,
                           Supplier<String> projectNameSupplier,
                           StructuredLogger logger) {
        this.questSupplier = Objects.requireNonNull(questSupplier, "questSupplier");
        this.projectNameSupplier = Objects.requireNonNull(projectNameSupplier, "projectNameSupplier");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public synchronized void start(int minutes) {
        updateIntervalInternal(minutes);
        if (intervalMinutes <= 0) {
            shutdownExecutor();
            return;
        }
        if (executor != null && !executor.isShutdown()) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "ftbq-autosave");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleAtFixedRate(this::safeAutosave, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
    }

    public synchronized void updateInterval(int minutes) {
        updateIntervalInternal(minutes);
        if (intervalMinutes <= 0) {
            shutdownExecutor();
        } else {
            restartExecutor();
        }
    }

    public int getIntervalMinutes() {
        return intervalMinutes;
    }

    public synchronized void stop() {
        shutdownExecutor();
    }

    public void flushNow() {
        QuestFile questFile = questSupplier.get();
        if (questFile == null) {
            return;
        }
        Path autosaveFile = autosaveFile();
        try {
            Files.createDirectories(autosaveFile.getParent());
            MAPPER.writeValue(autosaveFile.toFile(), questFile);
            logger.debug("Autosave flushed",
                    StructuredLogger.field("file", autosaveFile.toString()),
                    StructuredLogger.field("timestamp", Instant.now().toString()));
        } catch (IOException ex) {
            logger.warn("Autosave flush failed", ex, StructuredLogger.field("file", autosaveFile.toString()));
        }
    }

    public boolean hasAutosave() {
        return Files.exists(autosaveFile());
    }

    public Optional<QuestFile> readAutosave() {
        Path autosaveFile = autosaveFile();
        if (!Files.exists(autosaveFile)) {
            return Optional.empty();
        }
        try {
            QuestFile questFile = MAPPER.readValue(autosaveFile.toFile(), QuestFile.class);
            logger.info("Loaded autosave snapshot",
                    StructuredLogger.field("file", autosaveFile.toString()));
            return Optional.of(questFile);
        } catch (IOException ex) {
            logger.warn("Failed to read autosave", ex, StructuredLogger.field("file", autosaveFile.toString()));
            return Optional.empty();
        }
    }

    public void deleteAutosave() {
        Path autosaveFile = autosaveFile();
        try {
            Files.deleteIfExists(autosaveFile);
        } catch (IOException ex) {
            logger.warn("Failed to delete autosave", ex, StructuredLogger.field("file", autosaveFile.toString()));
        }
    }

    private void safeAutosave() {
        if (intervalMinutes <= 0) {
            return;
        }
        QuestFile questFile = questSupplier.get();
        if (questFile == null) {
            return;
        }
        Path autosaveFile = autosaveFile();
        try {
            Files.createDirectories(autosaveFile.getParent());
            MAPPER.writeValue(autosaveFile.toFile(), questFile);
            logger.debug("Autosave completed",
                    StructuredLogger.field("file", autosaveFile.toString()),
                    StructuredLogger.field("timestamp", Instant.now().toString()));
        } catch (IOException ex) {
            logger.warn("Scheduled autosave failed", ex, StructuredLogger.field("file", autosaveFile.toString()));
        }
    }

    private void restartExecutor() {
        shutdownExecutor();
        start(intervalMinutes);
    }

    private void shutdownExecutor() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void updateIntervalInternal(int minutes) {
        intervalMinutes = Math.max(0, minutes);
    }

    private Path autosaveFile() {
        Path autosaveDir = UserSettings.autosaveDirectory();
        String projectName = projectNameSupplier.get();
        if (projectName == null || projectName.isBlank()) {
            projectName = "project";
        }
        String safeName = sanitize(projectName);
        if (safeName.isBlank()) {
            safeName = HexId.generate();
        }
        return autosaveDir.resolve(safeName + ".autosave.json");
    }

    private String sanitize(String value) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == '-') {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        return builder.toString();
    }
}
