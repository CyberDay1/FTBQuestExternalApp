package dev.ftbq.editor.services;

import java.util.Objects;

public interface CommandBus {
    void publish(Object event);

    static CommandBus noop() {
        return event -> { };
    }

    static CommandBus logging(java.util.function.Consumer<Object> logger) {
        Objects.requireNonNull(logger, "logger");
        return logger::accept;
    }
}
