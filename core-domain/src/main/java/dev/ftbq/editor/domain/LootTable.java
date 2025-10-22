package dev.ftbq.editor.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Simplified loot table representation.
 */
public record LootTable(String id, List<LootPool> pools) {

    public LootTable {
        Objects.requireNonNull(id, "id");
        pools = List.copyOf(Objects.requireNonNull(pools, "pools"));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private final List<LootPool> pools = new ArrayList<>();

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

        public LootTable build() {
            return new LootTable(id, pools);
        }
    }
}
