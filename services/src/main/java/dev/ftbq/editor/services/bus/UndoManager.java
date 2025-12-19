package dev.ftbq.editor.services.bus;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages undo/redo history for {@link UndoableCommand} instances dispatched through the {@link CommandBus}.
 */
public final class UndoManager implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(UndoManager.class);

    private final CommandBus commandBus;
    private final Path historyFile;
    private final Duration debounce;
    private final ObjectMapper objectMapper;
    private final Map<String, UndoableCommandFactory> factories = new ConcurrentHashMap<>();
    private final Deque<HistoryEntry> undoStack = new ArrayDeque<>();
    private final Deque<HistoryEntry> redoStack = new ArrayDeque<>();
    private final Object lock = new Object();
    private final AtomicBoolean replaying = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "undo-history-writer");
        thread.setDaemon(true);
        return thread;
    });

    private ScheduledFuture<?> pendingWrite;

    public UndoManager(CommandBus commandBus, Path historyFile, Duration debounce) {
        this.commandBus = Objects.requireNonNull(commandBus, "commandBus");
        this.historyFile = Objects.requireNonNull(historyFile, "historyFile");
        this.debounce = Objects.requireNonNull(debounce, "debounce");
        this.objectMapper = new ObjectMapper();
        commandBus.subscribeAll(this::handleDispatched);
        loadHistory();
    }

    public void registerFactory(String type, UndoableCommandFactory factory) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(factory, "factory");
        factories.put(type, factory);
    }

    public boolean undo() {
        HistoryEntry entry;
        synchronized (lock) {
            entry = undoStack.pollLast();
        }
        if (entry == null) {
            return false;
        }
        if (!dispatch(entry.inverse())) {
            synchronized (lock) {
                undoStack.addLast(entry);
            }
            return false;
        }
        synchronized (lock) {
            redoStack.addLast(entry);
        }
        schedulePersist();
        return true;
    }

    public boolean redo() {
        HistoryEntry entry;
        synchronized (lock) {
            entry = redoStack.pollLast();
        }
        if (entry == null) {
            return false;
        }
        if (!dispatch(entry.command())) {
            synchronized (lock) {
                redoStack.addLast(entry);
            }
            return false;
        }
        synchronized (lock) {
            undoStack.addLast(entry);
        }
        schedulePersist();
        return true;
    }

    public boolean canUndo() {
        synchronized (lock) {
            return !undoStack.isEmpty();
        }
    }

    public boolean canRedo() {
        synchronized (lock) {
            return !redoStack.isEmpty();
        }
    }

    private void handleDispatched(Command command) {
        if (!(command instanceof UndoableCommand undoable)) {
            return;
        }
        if (replaying.get()) {
            return;
        }
        UndoableCommand inverse = Objects.requireNonNull(undoable.inverse(), "inverse");
        HistoryEntry entry = new HistoryEntry(serialize(undoable), serialize(inverse));
        synchronized (lock) {
            undoStack.addLast(entry);
            redoStack.clear();
        }
        schedulePersist();
    }

    private boolean dispatch(StoredCommand stored) {
        UndoableCommandFactory factory = factories.get(stored.type());
        if (factory == null) {
            throw new IllegalStateException("No undo factory registered for command type " + stored.type());
        }
        ObjectNode payload = stored.payload().deepCopy();
        UndoableCommand command = factory.create(payload);
        replaying.set(true);
        try {
            commandBus.dispatch(command);
            return true;
        } finally {
            replaying.set(false);
        }
    }

    private StoredCommand serialize(UndoableCommand command) {
        ObjectNode payload = command.toPayload();
        if (payload == null) {
            payload = JsonNodeFactory.instance.objectNode();
        }
        return new StoredCommand(command.type(), payload.deepCopy());
    }

    private void schedulePersist() {
        synchronized (lock) {
            if (pendingWrite != null) {
                pendingWrite.cancel(false);
            }
            History snapshot = snapshot();
            pendingWrite = scheduler.schedule(() -> writeHistory(snapshot), debounce.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private History snapshot() {
        List<HistoryEntry> undoCopy;
        List<HistoryEntry> redoCopy;
        synchronized (lock) {
            undoCopy = new ArrayList<>(undoStack);
            redoCopy = new ArrayList<>(redoStack);
        }
        return new History(undoCopy.stream().map(HistoryEntry::toStored).toList(),
                redoCopy.stream().map(HistoryEntry::toStored).toList());
    }

    private void writeHistory(History history) {
        try {
            Path parent = historyFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (OutputStream out = Files.newOutputStream(historyFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(out, history);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to persist undo history: {}", e.getMessage());
        }
    }

    private void loadHistory() {
        if (!Files.exists(historyFile)) {
            return;
        }
        try (InputStream in = Files.newInputStream(historyFile)) {
            History history = objectMapper.readValue(in, History.class);
            synchronized (lock) {
                undoStack.clear();
                redoStack.clear();
                for (HistoryEntryRecord record : history.undo()) {
                    undoStack.addLast(record.toEntry());
                }
                for (HistoryEntryRecord record : history.redo()) {
                    redoStack.addLast(record.toEntry());
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to load undo history: {}", e.getMessage());
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }

    private record StoredCommand(String type, ObjectNode payload) {
        @JsonCreator
        StoredCommand(@JsonProperty("type") String type,
                      @JsonProperty("payload") ObjectNode payload) {
            this.type = Objects.requireNonNull(type, "type");
            this.payload = payload == null ? JsonNodeFactory.instance.objectNode() : payload;
        }
    }

    private static final class HistoryEntry {
        private final StoredCommand command;
        private final StoredCommand inverse;

        private HistoryEntry(StoredCommand command, StoredCommand inverse) {
            this.command = command;
            this.inverse = inverse;
        }

        private StoredCommand command() {
            return command;
        }

        private StoredCommand inverse() {
            return inverse;
        }

        private HistoryEntryRecord toStored() {
            return new HistoryEntryRecord(command, inverse);
        }
    }

    private record History(List<HistoryEntryRecord> undo, List<HistoryEntryRecord> redo) {
        @JsonCreator
        History(@JsonProperty("undo") List<HistoryEntryRecord> undo,
                @JsonProperty("redo") List<HistoryEntryRecord> redo) {
            this.undo = undo == null ? List.of() : List.copyOf(undo);
            this.redo = redo == null ? List.of() : List.copyOf(redo);
        }
    }

    private record HistoryEntryRecord(StoredCommand command, StoredCommand inverse) {
        @JsonCreator
        HistoryEntryRecord(@JsonProperty("command") StoredCommand command,
                           @JsonProperty("inverse") StoredCommand inverse) {
            this.command = Objects.requireNonNull(command, "command");
            this.inverse = Objects.requireNonNull(inverse, "inverse");
        }

        private HistoryEntry toEntry() {
            return new HistoryEntry(new StoredCommand(command.type(), command.payload().deepCopy()),
                    new StoredCommand(inverse.type(), inverse.payload().deepCopy()));
        }
    }
}
