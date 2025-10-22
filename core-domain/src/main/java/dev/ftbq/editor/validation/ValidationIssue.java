package dev.ftbq.editor.validation;

import java.util.Objects;

public record ValidationIssue(String severity, String path, String message) {
    public ValidationIssue {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(message, "message");
    }
}
