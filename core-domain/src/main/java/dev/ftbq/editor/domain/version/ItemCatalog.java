package dev.ftbq.editor.domain.version;

import dev.ftbq.editor.domain.ItemRef;
import java.util.Collection;

/**
 * Represents a collection of items for a particular Minecraft version or mod set.
 */
public interface ItemCatalog {
    Collection<ItemRef> items();
}
