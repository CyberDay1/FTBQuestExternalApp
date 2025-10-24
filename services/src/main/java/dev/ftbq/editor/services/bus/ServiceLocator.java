package dev.ftbq.editor.services.bus;

import dev.ftbq.editor.services.logging.AppLoggerFactory;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;

/**
 * Provides global access to shared service instances used by the application.
 */
public final class ServiceLocator {

    private static final CommandBus COMMAND_BUS = new CommandBus();
    private static final EventBus EVENT_BUS = new EventBus();
    private static final UndoManager UNDO_MANAGER = createUndoManager();
    private static volatile AppLoggerFactory LOGGER_FACTORY = AppLoggerFactory.create();

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

    public static AppLoggerFactory loggerFactory() {
        return LOGGER_FACTORY;
    }

    public static void overrideLoggerFactory(AppLoggerFactory loggerFactory) {
        LOGGER_FACTORY = Objects.requireNonNull(loggerFactory, "loggerFactory");
    }

    private static UndoManager createUndoManager() {
        try {
            return new UndoManager(COMMAND_BUS, Path.of(".cache", "history.json"), Duration.ofMillis(500));
        } catch (RuntimeException ex) {
            throw ex;
        }
    }
}
