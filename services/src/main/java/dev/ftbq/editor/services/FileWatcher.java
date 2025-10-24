package dev.ftbq.editor.services;

import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.io.importer.Importer;
import dev.ftbq.editor.services.bus.EventBus;
import dev.ftbq.editor.services.events.PackReloaded;
import dev.ftbq.editor.services.logging.AppLoggerFactory;
import dev.ftbq.editor.services.logging.StructuredLogger;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Watches a quest pack directory tree and reloads it via the {@link Importer}
 * when filesystem changes are detected. Reload events are debounced to avoid
 * redundant reloads when many files change in quick succession.
 */
public final class FileWatcher implements AutoCloseable {

    private static final Duration DEFAULT_DEBOUNCE = Duration.ofMillis(500);

    private final Path packRoot;
    private final Importer importer;
    private final EventBus eventBus;
    private final Duration debounce;
    private final WatchService watchService;
    private final Map<WatchKey, Path> directories;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running;
    private final Thread watcherThread;
    private final Object reloadLock;
    private final StructuredLogger logger;

    private volatile ScheduledFuture<?> pendingReload;

    public FileWatcher(Path packRoot, Importer importer, EventBus eventBus, AppLoggerFactory loggerFactory) throws IOException {
        this(packRoot, importer, eventBus, DEFAULT_DEBOUNCE, loggerFactory);
    }

    public FileWatcher(Path packRoot,
                       Importer importer,
                       EventBus eventBus,
                       Duration debounce,
                       AppLoggerFactory loggerFactory) throws IOException {
        this(packRoot, importer, eventBus, debounce,
                Objects.requireNonNull(loggerFactory, "loggerFactory").create(FileWatcher.class));
    }

    public FileWatcher(Path packRoot,
                       Importer importer,
                       EventBus eventBus,
                       Duration debounce,
                       StructuredLogger logger) throws IOException {
        this.packRoot = normalizeRoot(packRoot);
        this.importer = Objects.requireNonNull(importer, "importer");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.debounce = debounce == null ? DEFAULT_DEBOUNCE : debounce;
        if (this.debounce.isNegative() || this.debounce.isZero()) {
            throw new IllegalArgumentException("debounce must be positive");
        }
        this.watchService = FileSystems.getDefault().newWatchService();
        this.directories = new ConcurrentHashMap<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "quest-pack-reloader");
            thread.setDaemon(true);
            return thread;
        });
        this.running = new AtomicBoolean(true);
        this.reloadLock = new Object();
        this.logger = Objects.requireNonNull(logger, "logger");

        registerAll(this.packRoot);
        this.watcherThread = new Thread(this::processEvents, "quest-pack-file-watcher");
        this.watcherThread.setDaemon(true);
        this.watcherThread.start();
        this.logger.info("Watching quest pack", StructuredLogger.field("root", this.packRoot));
    }

    private static Path normalizeRoot(Path packRoot) throws IOException {
        Objects.requireNonNull(packRoot, "packRoot");
        Path absolute = packRoot.toAbsolutePath().normalize();
        if (!Files.exists(absolute)) {
            throw new IOException("Quest pack directory does not exist: " + absolute);
        }
        if (!Files.isDirectory(absolute)) {
            throw new IOException("Quest pack path is not a directory: " + absolute);
        }
        return absolute;
    }

    private void registerAll(Path start) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, java.nio.file.attribute.BasicFileAttributes attrs) throws IOException {
                registerDirectory(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void registerDirectory(Path directory) throws IOException {
        Path absolute = directory.toAbsolutePath().normalize();
        WatchKey key = absolute.register(
            watchService,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY
        );
        directories.put(key, absolute);
    }

    private void processEvents() {
        try {
            while (running.get()) {
                WatchKey key;
                try {
                    key = watchService.take();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
                Path dir = directories.get(key);
                if (dir == null) {
                    key.reset();
                    continue;
                }
                boolean scheduleReload = false;
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == StandardWatchEventKinds.OVERFLOW) {
                        scheduleReload = true;
                        continue;
                    }
                    Path name = (Path) event.context();
                    Path child = dir.resolve(name);
                    if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                        try {
                            if (Files.isDirectory(child)) {
                                registerAll(child);
                            }
                        } catch (IOException ex) {
                            logger.warn("Failed to register new directory for watching", ex,
                                    StructuredLogger.field("directory", child));
                        }
                    }
                    scheduleReload = true;
                }
                boolean valid = key.reset();
                if (!valid) {
                    directories.remove(key);
                }
                if (scheduleReload) {
                    triggerReload();
                }
            }
        } catch (ClosedWatchServiceException closed) {
            // shutting down
        } finally {
            running.set(false);
        }
    }

    private void triggerReload() {
        synchronized (reloadLock) {
            if (pendingReload != null) {
                pendingReload.cancel(false);
            }
            pendingReload = scheduler.schedule(this::reloadPack, debounce.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void reloadPack() {
        if (!running.get()) {
            return;
        }
        try {
            QuestFile questFile = importer.importPack(packRoot);
            eventBus.publish(new PackReloaded(packRoot, questFile));
            logger.info("Reloaded quest pack", StructuredLogger.field("root", packRoot));
        } catch (IOException ex) {
            logger.warn("Failed to reload quest pack", ex, StructuredLogger.field("root", packRoot));
        }
    }

    @Override
    public void close() throws IOException {
        if (!running.getAndSet(false)) {
            return;
        }
        if (pendingReload != null) {
            pendingReload.cancel(false);
        }
        scheduler.shutdownNow();
        watcherThread.interrupt();
        watchService.close();
        logger.info("Stopped quest pack watcher", StructuredLogger.field("root", packRoot));
    }
}
