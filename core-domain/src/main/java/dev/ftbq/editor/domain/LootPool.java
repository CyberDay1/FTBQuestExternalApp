package dev.ftbq.editor.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Collection of entries drawn together.
 */
public record LootPool(String name,
                       int rolls,
                       List<LootEntry> entries,
                       List<LootCondition> conditions,
                       List<LootFunction> functions) {

    public LootPool {
        Objects.requireNonNull(name, "name");
        if (rolls < 1) {
            throw new IllegalArgumentException("rolls must be positive");
        }
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        conditions = List.copyOf(Objects.requireNonNull(conditions, "conditions"));
        functions = List.copyOf(Objects.requireNonNull(functions, "functions"));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String name;
        private int rolls = 1;
        private final List<LootEntry> entries = new ArrayList<>();
        private final List<LootCondition> conditions = new ArrayList<>();
        private final List<LootFunction> functions = new ArrayList<>();

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder rolls(int rolls) {
            this.rolls = rolls;
            return this;
        }

        public Builder addEntry(LootEntry entry) {
            this.entries.add(Objects.requireNonNull(entry, "entry"));
            return this;
        }

        public Builder entries(List<LootEntry> entries) {
            this.entries.clear();
            this.entries.addAll(Objects.requireNonNull(entries, "entries"));
            return this;
        }

        public Builder addCondition(LootCondition condition) {
            this.conditions.add(Objects.requireNonNull(condition, "condition"));
            return this;
        }

        public Builder conditions(List<LootCondition> conditions) {
            this.conditions.clear();
            this.conditions.addAll(Objects.requireNonNull(conditions, "conditions"));
            return this;
        }

        public Builder addFunction(LootFunction function) {
            this.functions.add(Objects.requireNonNull(function, "function"));
            return this;
        }

        public Builder functions(List<LootFunction> functions) {
            this.functions.clear();
            this.functions.addAll(Objects.requireNonNull(functions, "functions"));
            return this;
        }

        public LootPool build() {
            return new LootPool(name, rolls, entries, conditions, functions);
        }
    }
}
