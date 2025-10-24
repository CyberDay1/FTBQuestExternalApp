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
    private final List<Consumer<? super Command>> globalListeners = new ArrayList<>();

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
        List<Consumer<? super Command>> global;
        synchronized (this) {
            listeners = handlers.get(command.getClass());
            if (listeners != null) {
                listeners = new ArrayList<>(listeners);
            }
            if (globalListeners.isEmpty()) {
                global = null;
            } else {
                global = new ArrayList<>(globalListeners);
            }
        }
        if (listeners == null || listeners.isEmpty()) {
            listeners = List.of();
        }
        for (Consumer<? super Command> listener : listeners) {
            listener.accept(command);
        }
        if (global != null && !global.isEmpty()) {
            for (Consumer<? super Command> listener : global) {
                listener.accept(command);
            }
        }
    }

    public void subscribeAll(Consumer<? super Command> listener) {
        Objects.requireNonNull(listener, "listener");
        synchronized (this) {
            globalListeners.add(listener);
        }
    }
}
