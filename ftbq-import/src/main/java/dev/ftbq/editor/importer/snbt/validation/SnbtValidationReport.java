package dev.ftbq.editor.importer.snbt.validation;

import dev.ftbq.editor.validation.ValidationIssue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class SnbtValidationReport {
    private final List<ValidationIssue> issues;

    public SnbtValidationReport(List<ValidationIssue> issues) {
        Objects.requireNonNull(issues, "issues");
        this.issues = List.copyOf(issues);
    }

    public List<ValidationIssue> issues() {
        return issues;
    }

    public List<ValidationIssue> errors() {
        List<ValidationIssue> filtered = new ArrayList<>();
        for (ValidationIssue issue : issues) {
            if ("ERROR".equalsIgnoreCase(issue.severity())) {
                filtered.add(issue);
            }
        }
        return Collections.unmodifiableList(filtered);
    }

    public List<ValidationIssue> warnings() {
        List<ValidationIssue> filtered = new ArrayList<>();
        for (ValidationIssue issue : issues) {
            if (!"ERROR".equalsIgnoreCase(issue.severity())) {
                filtered.add(issue);
            }
        }
        return Collections.unmodifiableList(filtered);
    }

    public boolean valid() {
        for (ValidationIssue issue : issues) {
            if ("ERROR".equalsIgnoreCase(issue.severity())) {
                return false;
            }
        }
        return true;
    }
}
