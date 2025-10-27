package dev.ftbq.editor.services.bus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Minimal synchronous event bus. */
public final class EventBus {
    private final List<Consumer<Event>> handlers = new ArrayList<>();

    public void subscribe(Consumer<Event> handler) {
        Objects.requireNonNull(handler, "handler");
        handlers.add(handler);
    }

    public void publish(Event event) {
        Objects.requireNonNull(event, "event");
        for (Consumer<Event> h : handlers) h.accept(event);
    }
}


