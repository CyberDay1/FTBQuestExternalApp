package dev.ftbq.editor.io;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.ftbq.editor.domain.LootCondition;
import dev.ftbq.editor.domain.LootEntry;
import dev.ftbq.editor.domain.LootFunction;
import dev.ftbq.editor.domain.LootPool;
import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.io.model.ItemRefData;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class LootTableJson {
    private LootTableJson() {
    }

    public static void save(LootTable lootTable, Path root) throws IOException {
        Objects.requireNonNull(lootTable, "lootTable");
        Objects.requireNonNull(root, "root");
        Path relative = resourceLocationToPath(lootTable.id());
        Path jsonPath = root.resolve(relative + ".json");
        Files.createDirectories(jsonPath.getParent());
        LootTableData data = toData(lootTable);
        JsonConfig.OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), data);
    }

    public static LootTable load(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        LootTableData data = JsonConfig.OBJECT_MAPPER.readValue(file.toFile(), LootTableData.class);
        return fromData(data);
    }

    public static List<LootTable> loadAll(Path root) throws IOException {
        Objects.requireNonNull(root, "root");
        if (!Files.exists(root)) {
            return List.of();
        }
        List<LootTable> tables = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            tables.add(load(path));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        tables.sort(Comparator.comparing(LootTable::id));
        return tables;
    }

    static LootTableData toData(LootTable lootTable) {
        return new LootTableData(
                lootTable.id(),
                lootTable.pools().stream().map(LootPoolData::fromDomain).collect(Collectors.toList())
        );
    }

    static LootTable fromData(LootTableData data) {
        return new LootTable(
                data.id(),
                data.pools().stream().map(LootPoolData::toDomain).collect(Collectors.toList())
        );
    }

    private static Path resourceLocationToPath(String id) {
        int colon = id.indexOf(':');
        String pathPart = colon >= 0 ? id.substring(colon + 1) : id;
        String[] segments = pathPart.split("/");
        Path path = Path.of(segments[0]);
        for (int i = 1; i < segments.length; i++) {
            path = path.resolve(segments[i]);
        }
        return path;
    }

    static record LootTableData(@JsonProperty("id") String id,
                                @JsonProperty("pools") List<LootPoolData> pools) {
    }

    static record LootPoolData(@JsonProperty("name") String name,
                               @JsonProperty("rolls") int rolls,
                               @JsonProperty("entries") List<LootEntryData> entries,
                               @JsonProperty("conditions") List<LootConditionData> conditions,
                               @JsonProperty("functions") List<LootFunctionData> functions) {

        static LootPoolData fromDomain(LootPool pool) {
            return new LootPoolData(
                    pool.name(),
                    pool.rolls(),
                    pool.entries().stream().map(LootEntryData::fromDomain).collect(Collectors.toList()),
                    pool.conditions().stream().map(LootConditionData::fromDomain).collect(Collectors.toList()),
                    pool.functions().stream().map(LootFunctionData::fromDomain).collect(Collectors.toList())
            );
        }

        LootPool toDomain() {
            return new LootPool(
                    name,
                    rolls,
                    entries.stream().map(LootEntryData::toDomain).collect(Collectors.toList()),
                    conditions.stream().map(LootConditionData::toDomain).collect(Collectors.toList()),
                    functions.stream().map(LootFunctionData::toDomain).collect(Collectors.toList())
            );
        }
    }

    static record LootEntryData(@JsonProperty("item") ItemRefData item,
                                @JsonProperty("weight") double weight) {

        static LootEntryData fromDomain(LootEntry entry) {
            return new LootEntryData(ItemRefData.fromDomain(entry.item()), entry.weight());
        }

        LootEntry toDomain() {
            return new LootEntry(item.toDomain(), weight);
        }
    }

    static record LootConditionData(@JsonProperty("type") String type,
                                    @JsonProperty("parameters") Map<String, Object> parameters) {

        static LootConditionData fromDomain(LootCondition condition) {
            return new LootConditionData(condition.type(), condition.parameters());
        }

        LootCondition toDomain() {
            return new LootCondition(type, parameters == null ? Map.of() : parameters);
        }
    }

    static record LootFunctionData(@JsonProperty("type") String type,
                                   @JsonProperty("parameters") Map<String, Object> parameters) {

        static LootFunctionData fromDomain(LootFunction function) {
            return new LootFunctionData(function.type(), function.parameters());
        }

        LootFunction toDomain() {
            return new LootFunction(type, parameters == null ? Map.of() : parameters);
        }
    }
}
