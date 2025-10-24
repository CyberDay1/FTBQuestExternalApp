package dev.ftbq.editor.services.bus;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Provides global access to shared service instances used by the application.
 */
public final class ServiceLocator {

    private static final CommandBus COMMAND_BUS = new CommandBus();
    private static final EventBus EVENT_BUS = new EventBus();
    private static final UndoManager UNDO_MANAGER = createUndoManager();

    private ServiceLocator() {
        // utility class
    }

    public static CommandBus commandBus() {
        return COMMAND_BUS;
    }

    public static EventBus eventBus() {
        return EVENT_BUS;
    }

    public static UndoManager undoManager() {
        return UNDO_MANAGER;
    }

    private static UndoManager createUndoManager() {
        try {
            return new UndoManager(COMMAND_BUS, Path.of(".cache", "history.json"), Duration.ofMillis(500));
        } catch (RuntimeException ex) {
            throw ex;
        }
    }
}
