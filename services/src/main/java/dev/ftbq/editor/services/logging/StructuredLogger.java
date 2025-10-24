package dev.ftbq.editor.services.logging;

import java.util.Objects;
import org.slf4j.Logger;

/**
 * Lightweight wrapper around {@link Logger} that emits structured key-value metadata
 * alongside the human readable message. Messages are formatted so they remain readable
 * in simple console output while still allowing log aggregators to parse fields.
 */
public final class StructuredLogger {

    private final Logger delegate;

    StructuredLogger(Logger delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    public void info(String message, LogField... fields) {
        delegate.info(format(message, fields));
    }

    public void debug(String message, LogField... fields) {
        delegate.debug(format(message, fields));
    }

    public void warn(String message, LogField... fields) {
        delegate.warn(format(message, fields));
    }

    public void warn(String message, Throwable error, LogField... fields) {
        delegate.warn(format(message, fields), error);
    }

    public void error(String message, Throwable error, LogField... fields) {
        delegate.error(format(message, fields), error);
    }

    private static String format(String message, LogField... fields) {
        if (fields == null || fields.length == 0) {
            return message;
        }
        StringBuilder builder = new StringBuilder(message == null ? "" : message);
        builder.append(" |");
        for (LogField field : fields) {
            if (field == null) {
                continue;
            }
            builder.append(' ')
                    .append(field.key())
                    .append('=')
                    .append(String.valueOf(field.value()));
        }
        return builder.toString();
    }

    public static LogField field(String key, Object value) {
        return new LogField(key, value);
    }

    public record LogField(String key, Object value) {
        public LogField {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("key must not be blank");
            }
        }
    }
}
