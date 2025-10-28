package dev.ftbq.editor.importer.snbt.validation;

import dev.ftbq.editor.validation.ValidationIssue;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SnbtSchemaValidator {
    private final SnbtSchemaNode root;

    public SnbtSchemaValidator(SnbtSchemaNode root) {
        this.root = Objects.requireNonNull(root, "root");
    }

    public List<ValidationIssue> validate(Object value) {
        List<ValidationIssue> issues = new ArrayList<>();
        root.validate(value, ValidationPath.root(), issues);
        return issues;
    }
}
