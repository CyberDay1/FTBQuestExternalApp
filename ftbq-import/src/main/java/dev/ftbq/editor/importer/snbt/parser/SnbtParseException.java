package dev.ftbq.editor.importer.snbt.parser;

/**
 * Exception thrown when SNBT cannot be parsed.
 */
public class SnbtParseException extends RuntimeException {

    public SnbtParseException(String message) {
        super(message);
    }

    public SnbtParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
