package dev.ftbq.editor.services.generator;

import dev.ftbq.editor.services.mods.ModRegistryService;
import dev.ftbq.editor.services.mods.RegisteredMod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Captures the mods selected by the user for prompt context.
 */
public final class ModSelection {

    private final List<RegisteredMod> mods;
    private final int limit;

    public ModSelection(List<RegisteredMod> mods) {
        this(mods, ModRegistryService.MAX_SELECTION);
    }

    public ModSelection(List<RegisteredMod> mods, int limit) {
        Objects.requireNonNull(mods, "mods");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (mods.size() > limit) {
            throw new IllegalArgumentException("Mod selection exceeds limit " + limit + " (" + mods.size() + ")");
        }
        this.mods = Collections.unmodifiableList(new ArrayList<>(mods));
        this.limit = limit;
    }

    public List<RegisteredMod> mods() {
        return mods;
    }

    public int limit() {
        return limit;
    }

    public int count() {
        return mods.size();
    }
}
