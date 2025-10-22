package dev.ftbq.editor.io;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import java.util.LinkedHashMap;
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
        return LootTableData.fromDomain(lootTable);
    }

    static LootTable fromData(LootTableData data) {
        return data.toDomain();
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

    private static Map<String, JsonNode> extrasToMap(ObjectNode extras) {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        extras.fields().forEachRemaining(entry -> map.put(entry.getKey(), entry.getValue()));
        return map;
    }

    static final class LootTableData {
        @JsonProperty("id")
        private String id;
        @JsonProperty("pools")
        private List<LootPoolData> pools = List.of();
        @JsonIgnore
        private ObjectNode extras = JsonNodeFactory.instance.objectNode();

        LootTableData() {
        }

        static LootTableData fromDomain(LootTable lootTable) {
            LootTableData data = new LootTableData();
            data.id = lootTable.id();
            data.pools = lootTable.pools().stream()
                    .map(LootPoolData::fromDomain)
                    .collect(Collectors.toList());
            data.extras = lootTable.extras().deepCopy();
            return data;
        }

        LootTable toDomain() {
            List<LootPool> domainPools = safeList(pools).stream()
                    .map(LootPoolData::toDomain)
                    .collect(Collectors.toList());
            return new LootTable(id, domainPools, extras());
        }

        @JsonAnySetter
        public void addExtra(String name, JsonNode value) {
            extras().set(name, value);
        }

        @JsonAnyGetter
        public Map<String, JsonNode> getExtrasForJson() {
            return extrasToMap(extras());
        }

        @JsonIgnore
        ObjectNode extras() {
            if (extras == null) {
                extras = JsonNodeFactory.instance.objectNode();
            }
            return extras;
        }

        private static <T> List<T> safeList(List<T> value) {
            return value == null ? List.of() : value;
        }
    }

    static final class LootPoolData {
        @JsonProperty("name")
        private String name;
        @JsonProperty("rolls")
        private int rolls;
        @JsonProperty("entries")
        private List<LootEntryData> entries = List.of();
        @JsonProperty("conditions")
        private List<LootConditionData> conditions = List.of();
        @JsonProperty("functions")
        private List<LootFunctionData> functions = List.of();
        @JsonIgnore
        private ObjectNode extras = JsonNodeFactory.instance.objectNode();

        LootPoolData() {
        }

        static LootPoolData fromDomain(LootPool pool) {
            LootPoolData data = new LootPoolData();
            data.name = pool.name();
            data.rolls = pool.rolls();
            data.entries = pool.entries().stream()
                    .map(LootEntryData::fromDomain)
                    .collect(Collectors.toList());
            data.conditions = pool.conditions().stream()
                    .map(LootConditionData::fromDomain)
                    .collect(Collectors.toList());
            data.functions = pool.functions().stream()
                    .map(LootFunctionData::fromDomain)
                    .collect(Collectors.toList());
            data.extras = pool.extras().deepCopy();
            return data;
        }

        LootPool toDomain() {
            List<LootEntry> domainEntries = safeList(entries).stream()
                    .map(LootEntryData::toDomain)
                    .collect(Collectors.toList());
            List<LootCondition> domainConditions = safeList(conditions).stream()
                    .map(LootConditionData::toDomain)
                    .collect(Collectors.toList());
            List<LootFunction> domainFunctions = safeList(functions).stream()
                    .map(LootFunctionData::toDomain)
                    .collect(Collectors.toList());
            return new LootPool(name, rolls, domainEntries, domainConditions, domainFunctions, extras());
        }

        @JsonAnySetter
        public void addExtra(String name, JsonNode value) {
            extras().set(name, value);
        }

        @JsonAnyGetter
        public Map<String, JsonNode> getExtrasForJson() {
            return extrasToMap(extras());
        }

        @JsonIgnore
        ObjectNode extras() {
            if (extras == null) {
                extras = JsonNodeFactory.instance.objectNode();
            }
            return extras;
        }

        private static <T> List<T> safeList(List<T> value) {
            return value == null ? List.of() : value;
        }
    }

    static final class LootEntryData {
        @JsonProperty("item")
        private ItemRefData item;
        @JsonProperty("weight")
        private double weight;
        @JsonIgnore
        private ObjectNode extras = JsonNodeFactory.instance.objectNode();

        LootEntryData() {
        }

        static LootEntryData fromDomain(LootEntry entry) {
            LootEntryData data = new LootEntryData();
            data.item = ItemRefData.fromDomain(entry.item());
            data.weight = entry.weight();
            data.extras = entry.extras().deepCopy();
            return data;
        }

        LootEntry toDomain() {
            return new LootEntry(item.toDomain(), weight, extras());
        }

        @JsonAnySetter
        public void addExtra(String name, JsonNode value) {
            extras().set(name, value);
        }

        @JsonAnyGetter
        public Map<String, JsonNode> getExtrasForJson() {
            return extrasToMap(extras());
        }

        @JsonIgnore
        ObjectNode extras() {
            if (extras == null) {
                extras = JsonNodeFactory.instance.objectNode();
            }
            return extras;
        }
    }

    static final class LootConditionData {
        @JsonProperty("type")
        private String type;
        @JsonProperty("parameters")
        private Map<String, Object> parameters = Map.of();
        @JsonIgnore
        private ObjectNode extras = JsonNodeFactory.instance.objectNode();

        LootConditionData() {
        }

        static LootConditionData fromDomain(LootCondition condition) {
            LootConditionData data = new LootConditionData();
            data.type = condition.type();
            data.parameters = condition.parameters();
            data.extras = condition.extras().deepCopy();
            return data;
        }

        LootCondition toDomain() {
            Map<String, Object> params = parameters == null ? Map.of() : parameters;
            return new LootCondition(type, params, extras());
        }

        @JsonAnySetter
        public void addExtra(String name, JsonNode value) {
            extras().set(name, value);
        }

        @JsonAnyGetter
        public Map<String, JsonNode> getExtrasForJson() {
            return extrasToMap(extras());
        }

        @JsonIgnore
        ObjectNode extras() {
            if (extras == null) {
                extras = JsonNodeFactory.instance.objectNode();
            }
            return extras;
        }
    }

    static final class LootFunctionData {
        @JsonProperty("type")
        private String type;
        @JsonProperty("parameters")
        private Map<String, Object> parameters = Map.of();
        @JsonIgnore
        private ObjectNode extras = JsonNodeFactory.instance.objectNode();

        LootFunctionData() {
        }

        static LootFunctionData fromDomain(LootFunction function) {
            LootFunctionData data = new LootFunctionData();
            data.type = function.type();
            data.parameters = function.parameters();
            data.extras = function.extras().deepCopy();
            return data;
        }

        LootFunction toDomain() {
            Map<String, Object> params = parameters == null ? Map.of() : parameters;
            return new LootFunction(type, params, extras());
        }

        @JsonAnySetter
        public void addExtra(String name, JsonNode value) {
            extras().set(name, value);
        }

        @JsonAnyGetter
        public Map<String, JsonNode> getExtrasForJson() {
            return extrasToMap(extras());
        }

        @JsonIgnore
        ObjectNode extras() {
            if (extras == null) {
                extras = JsonNodeFactory.instance.objectNode();
            }
            return extras;
        }
    }
}
