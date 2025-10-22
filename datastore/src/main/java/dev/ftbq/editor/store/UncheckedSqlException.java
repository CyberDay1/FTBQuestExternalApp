package dev.ftbq.editor.store;

/**
 * Runtime wrapper for SQL related errors to simplify DAO consumption.
 */
public final class UncheckedSqlException extends RuntimeException {
    public UncheckedSqlException(String message, Throwable cause) {
        super(message, cause);
    }
}
