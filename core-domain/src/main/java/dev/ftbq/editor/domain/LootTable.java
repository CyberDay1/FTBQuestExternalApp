package dev.ftbq.editor.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Simplified loot table representation.
 */
public record LootTable(String id, List<LootPool> pools, ObjectNode extras) {

    private static final String ICON_FIELD = "icon";

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

    public Optional<String> iconId() {
        JsonNode node = extras.get(ICON_FIELD);
        if (node == null || node.isNull()) {
            return Optional.empty();
        }
        String text = node.asText();
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(text);
    }

    public String iconIdOrDefault(String defaultIcon) {
        return iconId().orElse(defaultIcon);
    }

    public LootTable withIconId(String iconId) {
        ObjectNode copy = extras.deepCopy();
        if (iconId == null || iconId.isBlank()) {
            copy.remove(ICON_FIELD);
        } else {
            copy.put(ICON_FIELD, iconId);
        }
        return new LootTable(id, pools, copy);
    }

    public static final class Builder {
        private String id;
        private final List<LootPool> pools = new ArrayList<>();
        private ObjectNode extras = JsonNodeFactory.instance.objectNode();
        private String iconId;

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

        public Builder iconId(String iconId) {
            this.iconId = iconId;
            return this;
        }

        public Builder extras(ObjectNode extras) {
            this.extras = Objects.requireNonNull(extras, "extras");
            return this;
        }

        public LootTable build() {
            ObjectNode extrasCopy = extras.deepCopy();
            if (iconId != null) {
                if (iconId.isBlank()) {
                    extrasCopy.remove(ICON_FIELD);
                } else {
                    extrasCopy.put(ICON_FIELD, iconId);
                }
            }
            return new LootTable(id, pools, extrasCopy);
        }
    }
}
