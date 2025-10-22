package dev.ftbq.editor.io;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.ftbq.editor.domain.LootPool;
import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.io.JsonConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LootTableJsonTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTripRetainsUnknownFields() throws IOException {
        String json = """
                {
                  "id": "example:loot/test_table",
                  "pools": [
                    {
                      "name": "main",
                      "rolls": 2,
                      "entries": [
                        {
                          "item": {
                            "item": "minecraft:apple",
                            "count": 1
                          },
                          "weight": 1.0,
                          "entry_extra": {
                            "note": "value"
                          }
                        }
                      ],
                      "conditions": [
                        {
                          "type": "minecraft:random_chance",
                          "parameters": {
                            "chance": 0.5
                          },
                          "condition_extra": 42
                        }
                      ],
                      "functions": [
                        {
                          "type": "minecraft:set_count",
                          "parameters": {
                            "count": 3
                          },
                          "function_extra": "bonus"
                        }
                      ],
                      "pool_extra": true
                    }
                  ],
                  "table_extra": "keep_me"
                }
                """;

        Path input = tempDir.resolve("input.json");
        Files.writeString(input, json);

        LootTable loaded = LootTableJson.load(input);
        LootPool pool = loaded.pools().getFirst();

        assertTrue(loaded.extras().has("table_extra"));
        assertTrue(pool.extras().has("pool_extra"));
        assertTrue(pool.entries().getFirst().extras().has("entry_extra"));
        assertTrue(pool.conditions().getFirst().extras().has("condition_extra"));
        assertTrue(pool.functions().getFirst().extras().has("function_extra"));

        Path outputRoot = tempDir.resolve("out");
        LootTableJson.save(loaded, outputRoot);

        Path saved = outputRoot.resolve("loot").resolve("test_table.json");
        String savedJson = Files.readString(saved);

        ObjectNode savedTree = (ObjectNode) JsonConfig.OBJECT_MAPPER.readTree(savedJson);
        assertTrue(savedTree.has("table_extra"));

        JsonNode savedPool = savedTree.withArray("pools").get(0);
        assertTrue(savedPool.has("pool_extra"));

        JsonNode savedEntry = savedPool.withArray("entries").get(0);
        assertTrue(savedEntry.has("entry_extra"));

        JsonNode savedCondition = savedPool.withArray("conditions").get(0);
        assertTrue(savedCondition.has("condition_extra"));

        JsonNode savedFunction = savedPool.withArray("functions").get(0);
        assertTrue(savedFunction.has("function_extra"));
    }
}
