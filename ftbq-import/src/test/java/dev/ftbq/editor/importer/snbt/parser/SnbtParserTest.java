package dev.ftbq.editor.importer.snbt.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SnbtParserTest {

    @Test
    void parsesNestedCompoundWithCommentsAndVariousTypes() {
        String snbt = loadExample("complex_values.snbt");

        Map<String, Object> root = SnbtParser.parseRootCompound(snbt);

        assertEquals("Complex", root.get("name"));
        assertEquals(Boolean.TRUE, root.get("enabled"));
        assertEquals(Short.valueOf((short) 42), root.get("count"));
        assertEquals(3.14159d, root.get("ratio"));
        assertEquals(6.022e23, root.get("scientific"));
        assertEquals("diamond_sword", root.get("tag"));
        assertEquals("minecraft/items/diamond_sword", root.get("path"));

        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) root.get("list");
        assertEquals(5, list.size(), "Expected heterogeneous list to be parsed");
        assertEquals(1L, list.get(0));
        assertEquals(2L, list.get(1));
        assertEquals(Byte.valueOf((byte) 3), list.get(2));
        assertEquals("text", list.get(3));

        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) list.get(4);
        assertEquals(Boolean.TRUE, nested.get("inner"));

        @SuppressWarnings("unchecked")
        Map<String, Object> nestedCompound = (Map<String, Object>) root.get("nested");
        assertEquals("value", nestedCompound.get("key"));
        assertEquals("Line one\nLine two", nestedCompound.get("note"));
        assertEquals("quoted value", nestedCompound.get("quoted"));

        assertEquals(5L, root.get("afterComment"));
        assertEquals("value", root.get("withHash"));
        assertEquals("done", root.get("trailing"));
    }

    @Test
    void parseRootCompoundRejectsListRoot() {
        SnbtParseException ex = assertThrows(SnbtParseException.class,
                () -> SnbtParser.parseRootCompound("[1,2,3]"));

        assertTrue(ex.getMessage().contains("Root of SNBT must be a compound"));
    }

    @Test
    void parseRootCompoundRejectsTrailingContent() {
        SnbtParseException ex = assertThrows(SnbtParseException.class,
                () -> SnbtParser.parseRootCompound("{id:\"x\"} extra"));

        assertTrue(ex.getMessage().contains("Trailing content"),
                () -> "Unexpected error message: " + ex.getMessage());
    }

    @Test
    void parseRootCompoundProducesExpectedQuestStructure() {
        Map<String, Object> root = SnbtParser.parseRootCompound(loadExample("valid_pack.snbt"));

        assertEquals("example_pack", root.get("id"));
        assertEquals("Example Pack", root.get("title"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> chapters = (List<Map<String, Object>>) root.get("chapters");
        assertEquals(1, chapters.size());
        assertEquals("chapter1", chapters.get(0).get("id"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> quests = (List<Map<String, Object>>) chapters.get(0).get("quests");
        assertEquals(1, quests.size());
        assertEquals("quest1", quests.get(0).get("id"));
    }

    private String loadExample(String name) {
        try (var input = getClass().getResourceAsStream("/snbt/" + name)) {
            if (input == null) {
                throw new IllegalStateException("Missing SNBT example: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to read SNBT example " + name, ex);
        }
    }
}
