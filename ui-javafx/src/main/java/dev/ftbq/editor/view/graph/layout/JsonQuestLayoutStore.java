package dev.ftbq.editor.view.graph.layout;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.ftbq.editor.services.bus.ServiceLocator;
import dev.ftbq.editor.services.logging.StructuredLogger;
import javafx.geometry.Point2D;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Stores quest graph layout positions in a JSON file under the workspace's {@code .editor} directory.
 */
public final class JsonQuestLayoutStore implements QuestLayoutStore {

    private static final Duration DEFAULT_DEBOUNCE = Duration.ofMillis(500);

    private final StructuredLogger logger = ServiceLocator.loggerFactory().create(JsonQuestLayoutStore.class);
    private final Path layoutFile;
    private final Duration debounce;
    private final Object lock = new Object();
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService executor;
    private final Map<String, Map<String, NodePosition>> positions = new HashMap<>();

    private ScheduledFuture<?> pendingFlush;

    public JsonQuestLayoutStore(Path workspaceRoot) {
        this(workspaceRoot, DEFAULT_DEBOUNCE);
    }

    public JsonQuestLayoutStore(Path workspaceRoot, Duration debounce) {
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        Objects.requireNonNull(debounce, "debounce");
        this.debounce = debounce.isNegative() ? DEFAULT_DEBOUNCE : debounce;
        Path editorDir = workspaceRoot.resolve(".editor");
        this.layoutFile = editorDir.resolve("layout.json");
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        this.executor = createExecutor();
        loadFromDisk();
    }

    @Override
    public Optional<Point2D> getNodePos(String chapterId, String questId) {
        if (chapterId == null || chapterId.isBlank() || questId == null || questId.isBlank()) {
            return Optional.empty();
        }
        synchronized (lock) {
            Map<String, NodePosition> chapter = positions.get(chapterId);
            if (chapter == null) {
                return Optional.empty();
            }
            NodePosition pos = chapter.get(questId);
            if (pos == null) {
                return Optional.empty();
            }
            return Optional.of(new Point2D(pos.x(), pos.y()));
        }
    }

    @Override
    public void putNodePos(String chapterId, String questId, double x, double y) {
        if (chapterId == null || chapterId.isBlank() || questId == null || questId.isBlank()) {
            return;
        }
        synchronized (lock) {
            positions
                    .computeIfAbsent(chapterId, ignored -> new LinkedHashMap<>())
                    .put(questId, new NodePosition(x, y));
            scheduleFlushLocked();
        }
    }

    @Override
    public void removeQuest(String chapterId, String questId) {
        if (chapterId == null || chapterId.isBlank() || questId == null || questId.isBlank()) {
            return;
        }
        synchronized (lock) {
            Map<String, NodePosition> chapter = positions.get(chapterId);
            if (chapter == null) {
                return;
            }
            if (chapter.remove(questId) != null) {
                if (chapter.isEmpty()) {
                    positions.remove(chapterId);
                }
                scheduleFlushLocked();
            }
        }
    }

    @Override
    public void removeChapter(String chapterId) {
        if (chapterId == null || chapterId.isBlank()) {
            return;
        }
        synchronized (lock) {
            if (positions.remove(chapterId) != null) {
                scheduleFlushLocked();
            }
        }
    }

    @Override
    public void flush() {
        synchronized (lock) {
            flushLocked(true);
        }
    }

    private void scheduleFlushLocked() {
        if (debounce.isZero()) {
            flushLocked(false);
            return;
        }
        if (pendingFlush != null && !pendingFlush.isDone()) {
            pendingFlush.cancel(false);
        }
        long delay = Math.max(0, debounce.toMillis());
        pendingFlush = executor.schedule(this::flushAsync, delay, TimeUnit.MILLISECONDS);
    }

    private void flushAsync() {
        synchronized (lock) {
            pendingFlush = null;
            flushLocked(false);
        }
    }

    private void flushLocked(boolean cancelPending) {
        if (cancelPending && pendingFlush != null) {
            pendingFlush.cancel(false);
            pendingFlush = null;
        }
        Map<String, Map<String, NodePosition>> snapshot = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, NodePosition>> chapterEntry : positions.entrySet()) {
            Map<String, NodePosition> chapter = new LinkedHashMap<>(chapterEntry.getValue());
            if (!chapter.isEmpty()) {
                snapshot.put(chapterEntry.getKey(), chapter);
            }
        }
        if (snapshot.isEmpty() && !Files.exists(layoutFile)) {
            return;
        }
        try {
            Files.createDirectories(layoutFile.getParent());
            try (OutputStream output = Files.newOutputStream(layoutFile)) {
                objectMapper.writeValue(output, snapshot);
            }
        } catch (IOException ex) {
            logger.error("Failed to flush quest layout store", ex,
                    StructuredLogger.field("path", layoutFile.toString()));
        }
    }

    private void loadFromDisk() {
        if (!Files.exists(layoutFile)) {
            return;
        }
        TypeReference<Map<String, Map<String, NodePosition>>> type = new TypeReference<>() { };
        try (InputStream input = Files.newInputStream(layoutFile)) {
            Map<String, Map<String, NodePosition>> data = objectMapper.readValue(input, type);
            synchronized (lock) {
                positions.clear();
                data.forEach((chapterId, quests) -> {
                    if (chapterId == null || chapterId.isBlank() || quests == null || quests.isEmpty()) {
                        return;
                    }
                    Map<String, NodePosition> cleaned = new LinkedHashMap<>();
                    quests.forEach((questId, pos) -> {
                        if (questId != null && !questId.isBlank() && pos != null) {
                            cleaned.put(questId, pos);
                        }
                    });
                    if (!cleaned.isEmpty()) {
                        positions.put(chapterId, cleaned);
                    }
                });
            }
        } catch (IOException ex) {
            logger.error("Failed to load quest layout store", ex,
                    StructuredLogger.field("path", layoutFile.toString()));
        }
    }

    private ScheduledExecutorService createExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "quest-layout-store");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }

    private record NodePosition(double x, double y) { }
}
