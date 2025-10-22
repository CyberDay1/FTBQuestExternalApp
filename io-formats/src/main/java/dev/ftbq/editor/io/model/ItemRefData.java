package dev.ftbq.editor.io.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.ftbq.editor.domain.ItemRef;

import java.util.Objects;

public record ItemRefData(@JsonProperty("item") String itemId,
                          @JsonProperty("count") int count) {

    public ItemRefData {
        Objects.requireNonNull(itemId, "itemId");
    }

    public static ItemRefData fromDomain(ItemRef itemRef) {
        Objects.requireNonNull(itemRef, "itemRef");
        return new ItemRefData(itemRef.itemId(), itemRef.count());
    }

    public ItemRef toDomain() {
        return new ItemRef(itemId, count);
    }
}
