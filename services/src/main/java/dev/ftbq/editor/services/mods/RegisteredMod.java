package dev.ftbq.editor.services.mods;

import java.util.List;
import java.util.Objects;

/**
 * Immutable descriptor for a mod that has been registered with the editor.
 */
public record RegisteredMod(String modId,
                             String name,
                             String version,
                             List<String> itemIds,
                             String sourceJar) {

    public RegisteredMod {
        Objects.requireNonNull(modId, "modId");
        Objects.requireNonNull(itemIds, "itemIds");
        itemIds = List.copyOf(itemIds);
    }

    public String displayName() {
        if (name != null && !name.isBlank()) {
            return name;
        }
        return modId;
    }

    public int itemCount() {
        return itemIds.size();
    }
}
