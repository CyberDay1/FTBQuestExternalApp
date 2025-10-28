package dev.ftbq.editor.importer.snbt.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ftbq.editor.validation.ValidationIssue;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnbtValidationServiceTest {

    private static final String VALID_SNBT = """
            {
              id:"example_pack",
              title:"Example Pack",
              file_version:1,
              version:1,
              chapter_groups:[{
                id:"group",
                title:"Group",
                icon:"minecraft:book",
                chapter_ids:["chapter1"]
              }],
              chapters:[{
                id:"chapter1",
                title:"Chapter One",
                description:"Do the thing",
                icon:"minecraft:apple",
                background:"minecraft:textures/gui/book.png",
                quests:[{
                  id:"quest1",
                  title:"Quest One",
                  description:"Collect an item",
                  icon:"minecraft:apple",
                  tasks:[{type:"item", item:{id:"minecraft:stone"}}],
                  rewards:[{type:"xp_levels", amount:5}]
                }]
              }],
              loot_tables:[{
                id:"loot",
                pools:[]
              }]
            }
            """;

    private final SnbtValidationService service = new SnbtValidationService();

    @Test
    void validQuestPackProducesNoIssues() {
        SnbtValidationReport report = service.validate(VALID_SNBT);

        assertTrue(report.valid(), () -> "Expected validation to succeed but found issues: " + report.issues());
        assertTrue(report.issues().isEmpty(), () -> "Expected no validation issues but found: " + report.issues());
    }

    @Test
    void missingRequiredQuestFieldProducesError() {
        String snbt = """
                {
                  id:"example_pack",
                  title:"Example Pack",
                  file_version:1,
                  version:1,
                  chapter_groups:[{
                    id:"group",
                    title:"Group",
                    icon:"minecraft:book",
                    chapter_ids:["chapter1"]
                  }],
                  chapters:[{
                    id:"chapter1",
                    title:"Chapter One",
                    description:"Do the thing",
                    icon:"minecraft:apple",
                    background:"minecraft:textures/gui/book.png",
                    quests:[{
                      id:"quest1",
                      title:"Quest One",
                      description:"Collect an item",
                      tasks:[{type:"item", item:{id:"minecraft:stone"}}],
                      rewards:[{type:"xp_levels", amount:5}]
                    }]
                  }],
                  loot_tables:[]
                }
                """;

        SnbtValidationReport report = service.validate(snbt);

        assertFalse(report.valid(), () -> "Validation unexpectedly succeeded: " + report.issues());
        assertTrue(report.errors().stream()
                        .anyMatch(issue -> "$.chapters[0].quests[0].icon".equals(issue.path())
                                && issue.message().contains("Missing required property")),
                () -> "Expected missing icon error but got: " + report.errors());
    }

    @Test
    void chapterGroupMustListChapters() {
        String snbt = """
                {
                  id:"example_pack",
                  title:"Example Pack",
                  file_version:1,
                  version:1,
                  chapter_groups:[{
                    id:"group",
                    title:"Group",
                    icon:"minecraft:book"
                  }],
                  chapters:[{
                    id:"chapter1",
                    title:"Chapter One",
                    description:"Do the thing",
                    icon:"minecraft:apple",
                    background:"minecraft:textures/gui/book.png",
                    quests:[{
                      id:"quest1",
                      title:"Quest One",
                      description:"Collect an item",
                      icon:"minecraft:apple",
                      tasks:[{type:"item", item:{id:"minecraft:stone"}}],
                      rewards:[{type:"xp_levels", amount:5}]
                    }]
                  }],
                  loot_tables:[]
                }
                """;

        SnbtValidationReport report = service.validate(snbt);

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
        SnbtValidationReport report = service.validate("{id:\"oops");

        assertFalse(report.valid(), () -> "Parse failure should report errors but got: " + report.issues());
        assertEquals(1, report.errors().size(), () -> "Expected a single parse error but got: " + report.errors());
        ValidationIssue issue = report.errors().getFirst();
        assertEquals("$", issue.path());
        assertTrue(issue.message().startsWith("SNBT parse error:"),
                () -> "Unexpected parse error message: " + issue.message());
    }
}
