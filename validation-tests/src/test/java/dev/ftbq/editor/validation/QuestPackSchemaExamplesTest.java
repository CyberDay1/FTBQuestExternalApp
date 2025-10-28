package dev.ftbq.editor.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ftbq.editor.importer.snbt.validation.SnbtValidationReport;
import dev.ftbq.editor.importer.snbt.validation.SnbtValidationService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class QuestPackSchemaExamplesTest {

    private SnbtValidationService service;

    @BeforeEach
    void setUp() {
        service = new SnbtValidationService();
    }

    @Test
    void providedValidExamplePassesValidation() {
        String snbt = loadExample("valid_pack.snbt");

        SnbtValidationReport report = service.validate(snbt);

        assertTrue(report.valid(), () -> "Expected valid example to pass but got: " + report.issues());
    }

    @Test
    void providedInvalidExampleFailsValidation() {
        String snbt = loadExample("missing_icon_invalid.snbt");

        SnbtValidationReport report = service.validate(snbt);

        assertFalse(report.valid(), "Invalid example should fail schema validation");
        assertTrue(report.errors().stream()
                        .anyMatch(issue -> issue.path().equals("$.chapters[0].quests[0].icon")),
                () -> "Expected missing quest icon error but found: " + report.errors());
    }

    private String loadExample(String name) {
        try (var input = getClass().getResourceAsStream("/snbt/" + name)) {
            if (input == null) {
                throw new IllegalStateException("Missing validation example: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load validation example " + name, ex);
        }
    }
}
