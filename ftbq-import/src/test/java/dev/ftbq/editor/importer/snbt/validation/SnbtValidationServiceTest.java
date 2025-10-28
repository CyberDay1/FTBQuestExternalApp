package dev.ftbq.editor.importer.snbt.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ftbq.editor.validation.ValidationIssue;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnbtValidationServiceTest {

    private final SnbtValidationService service = new SnbtValidationService();

    @Test
    void validQuestPackProducesNoIssues() {
        SnbtValidationReport report = service.validate(loadExample("valid_pack.snbt"));

        assertTrue(report.valid(), () -> "Expected validation to succeed but found issues: " + report.issues());
        assertTrue(report.issues().isEmpty(), () -> "Expected no validation issues but found: " + report.issues());
    }

    @Test
    void missingRequiredQuestFieldProducesError() {
        SnbtValidationReport report = service.validate(loadExample("missing_icon_invalid.snbt"));

        assertFalse(report.valid(), () -> "Validation unexpectedly succeeded: " + report.issues());
        assertTrue(report.errors().stream()
                        .anyMatch(issue -> "$.chapters[0].quests[0].icon".equals(issue.path())
                                && issue.message().contains("Missing required property")),
                () -> "Expected missing icon error but got: " + report.errors());
    }

    @Test
    void chapterGroupMustListChapters() {
        SnbtValidationReport report = service.validate(loadExample("chapter_group_missing_ids_invalid.snbt"));

        assertFalse(report.valid(), () -> "Validation unexpectedly succeeded: " + report.issues());
        List<ValidationIssue> errors = report.errors();
        assertTrue(errors.stream()
                        .anyMatch(issue -> issue.message()
                                .contains("Chapter group must list chapters via 'chapter_ids' or 'chapters'.")),
                () -> "Expected schema rule failure but got: " + errors);
        assertTrue(errors.stream()
                        .anyMatch(issue -> issue.path().equals("$.chapter_groups[0].chapters")),
                () -> "Expected structural chapter reference error but got: " + errors);
    }

    @Test
    void malformedSnbtProducesParseError() {
        SnbtValidationReport report = service.validate(loadExample("malformed_missing_brace.snbt"));

        assertFalse(report.valid(), () -> "Parse failure should report errors but got: " + report.issues());
        assertEquals(1, report.errors().size(), () -> "Expected a single parse error but got: " + report.errors());
        ValidationIssue issue = report.errors().getFirst();
        assertEquals("$", issue.path());
        assertTrue(issue.message().startsWith("SNBT parse error:"),
                () -> "Unexpected parse error message: " + issue.message());
    }

    private String loadExample(String name) {
        try (var input = getClass().getResourceAsStream("/snbt/" + name)) {
            if (input == null) {
                throw new IllegalStateException("Missing SNBT example: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load SNBT example " + name, ex);
        }
    }
}
