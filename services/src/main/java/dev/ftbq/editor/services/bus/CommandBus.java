package dev.ftbq.editor.services.bus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Thread-safe in-memory command bus for dispatching commands to registered handlers.
 */
public class CommandBus {

    private final Map<Class<? extends Command>, List<Consumer<? super Command>>> handlers = new HashMap<>();

    /**
     * Registers a handler for the supplied command type.
     *
     * @param type    the command type to handle
     * @param handler the handler that will be invoked for each dispatched command of the given type
     * @param <T>     the specific command type
     */
    public <T extends Command> void subscribe(Class<T> type, Consumer<? super T> handler) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(handler, "handler");
        synchronized (this) {
            handlers
                .computeIfAbsent(type, key -> new ArrayList<>())
                .add(command -> handler.accept(type.cast(command)));
        }
    }

    /**
     * Dispatches the supplied command to every handler registered for its type.
     *
     * @param command the command to dispatch
     */
    public void dispatch(Command command) {
        Objects.requireNonNull(command, "command");
        List<Consumer<? super Command>> listeners;
        synchronized (this) {
            listeners = handlers.get(command.getClass());
            if (listeners != null) {
                listeners = new ArrayList<>(listeners);
            }
        }
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        for (Consumer<? super Command> listener : listeners) {
            listener.accept(command);
        }
    }
}
