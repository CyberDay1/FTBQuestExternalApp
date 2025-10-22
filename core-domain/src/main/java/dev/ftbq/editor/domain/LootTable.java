package dev.ftbq.editor.domain;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Simplified loot table representation.
 */
public record LootTable(String id, List<LootPool> pools, ObjectNode extras) {

    public LootTable {
        Objects.requireNonNull(id, "id");
        pools = List.copyOf(Objects.requireNonNull(pools, "pools"));
        extras = Objects.requireNonNull(extras, "extras").deepCopy();
    }

    public LootTable(String id, List<LootPool> pools) {
        this(id, pools, JsonNodeFactory.instance.objectNode());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private final List<LootPool> pools = new ArrayList<>();
        private ObjectNode extras = JsonNodeFactory.instance.objectNode();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder addPool(LootPool pool) {
            this.pools.add(Objects.requireNonNull(pool, "pool"));
            return this;
        }

        public Builder pools(List<LootPool> pools) {
            this.pools.clear();
            this.pools.addAll(Objects.requireNonNull(pools, "pools"));
            return this;
        }

        public Builder extras(ObjectNode extras) {
            this.extras = Objects.requireNonNull(extras, "extras");
            return this;
        }

        public LootTable build() {
            return new LootTable(id, pools, extras);
        }
    }
}
