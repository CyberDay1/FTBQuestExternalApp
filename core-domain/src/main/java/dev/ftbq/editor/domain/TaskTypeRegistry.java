package dev.ftbq.editor.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Registry for mapping task type identifiers to their implementation classes.
 */
public final class TaskTypeRegistry {

    private static final Map<String, Class<? extends Task>> TYPES = new LinkedHashMap<>();
    private static final Map<Class<? extends Task>, String> IDS = new LinkedHashMap<>();

    static {
        register("item", ItemTask.class);
        register("advancement", AdvancementTask.class);
        register("location", LocationTask.class);
        register("checkmark", CheckmarkTask.class);
        register("kill", KillTask.class);
        register("observation", ObservationTask.class);
        register("gamestage", StageTask.class);
        register("dimension", DimensionTask.class);
        register("biome", BiomeTask.class);
        register("structure", StructureTask.class);
        register("xp", XpTask.class);
        register("stat", StatTask.class);
        register("fluid", FluidTask.class);
        register("custom", CustomTask.class);
    }

    private TaskTypeRegistry() {
    }

    public static synchronized void register(String id, Class<? extends Task> type) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        String normalized = id.trim().toLowerCase();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Task id cannot be blank");
        }
        TYPES.put(normalized, type);
        IDS.put(type, normalized);
    }

    public static Set<String> ids() {
        return Collections.unmodifiableSet(TYPES.keySet());
    }

    public static Optional<Class<? extends Task>> typeFor(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(TYPES.get(id.trim().toLowerCase()));
    }

    public static Optional<String> idFor(Class<? extends Task> type) {
        return Optional.ofNullable(IDS.get(type));
    }
}
