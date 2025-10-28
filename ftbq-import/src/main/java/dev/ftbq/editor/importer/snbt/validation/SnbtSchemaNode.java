package dev.ftbq.editor.importer.snbt.validation;

import dev.ftbq.editor.validation.ValidationIssue;
import java.util.List;

interface SnbtSchemaNode {
    void validate(Object value, ValidationPath path, List<ValidationIssue> issues);
}
