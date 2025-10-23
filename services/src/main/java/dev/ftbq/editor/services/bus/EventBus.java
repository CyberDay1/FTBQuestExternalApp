package dev.ftbq.editor.services.bus;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Thread-safe in-memory event bus for publishing events to registered listeners.
 */
public class EventBus {

    private final Map<Class<? extends Event>, List<Consumer<? super Event>>> listeners = new HashMap<>();

    /**
     * Registers a listener for the supplied event type.
     *
     * @param type     the event type to listen for
     * @param listener the listener to invoke when the event is published
     * @param <T>      the specific event type
     */
    public <T extends Event> void subscribe(Class<T> type, Consumer<? super T> listener) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(listener, "listener");
        synchronized (this) {
            listeners
                .computeIfAbsent(type, key -> new ArrayList<>())
                .add(event -> listener.accept(type.cast(event)));
        }
    }

    /**
     * Publishes the supplied event to every registered listener of its type.
     *
     * @param event the event to publish
     */
    public void publish(Event event) {
        Objects.requireNonNull(event, "event");
        List<Consumer<? super Event>> consumers;
        synchronized (this) {
            consumers = listeners.get(event.getClass());
            if (consumers != null) {
                consumers = new ArrayList<>(consumers);
            }
        }
        if (consumers == null || consumers.isEmpty()) {
            return;
        }
        for (Consumer<? super Event> consumer : consumers) {
            consumer.accept(event);
        }
    }
}
