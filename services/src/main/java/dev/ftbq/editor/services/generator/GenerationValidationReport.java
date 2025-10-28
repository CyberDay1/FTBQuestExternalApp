package dev.ftbq.editor.services.generator;

import dev.ftbq.editor.validation.ValidationIssue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Summarizes validation issues produced while vetting generated content.
 */
public final class GenerationValidationReport {
    private final boolean passed;
    private final List<ValidationIssue> issues;

    public GenerationValidationReport(boolean passed, List<ValidationIssue> issues) {
        this.passed = passed;
        this.issues = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(issues, "issues")));
    }

    public boolean passed() {
        return passed;
    }

    public List<ValidationIssue> issues() {
        return issues;
    }

    public static GenerationValidationReport fromIssues(List<ValidationIssue> issues) {
        Objects.requireNonNull(issues, "issues");
        boolean passed = issues.stream().noneMatch(issue -> "error".equalsIgnoreCase(issue.severity()));
        return new GenerationValidationReport(passed, issues);
    }
}
