package dev.ftbq.editor.services.bus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Minimal synchronous command bus. */
public final class CommandBus {
    private final List<Consumer<Command>> handlers = new ArrayList<>();

    public void subscribe(Consumer<Command> handler) {
        Objects.requireNonNull(handler, "handler");
        handlers.add(handler);
    }

    public void dispatch(Command command) {
        Objects.requireNonNull(command, "command");
        for (Consumer<Command> h : handlers) h.accept(command);
    }
}
