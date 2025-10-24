package dev.ftbq.editor.services.logging;

import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory responsible for creating {@link StructuredLogger} instances.
 */
public final class AppLoggerFactory {

    private final Function<Class<?>, Logger> delegateFactory;

    private AppLoggerFactory(Function<Class<?>, Logger> delegateFactory) {
        this.delegateFactory = Objects.requireNonNull(delegateFactory, "delegateFactory");
    }

    public static AppLoggerFactory create() {
        return new AppLoggerFactory(LoggerFactory::getLogger);
    }

    public static AppLoggerFactory from(Function<Class<?>, Logger> provider) {
        return new AppLoggerFactory(provider);
    }

    public StructuredLogger create(Class<?> type) {
        Objects.requireNonNull(type, "type");
        return new StructuredLogger(delegateFactory.apply(type));
    }
}
