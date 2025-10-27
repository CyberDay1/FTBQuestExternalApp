package dev.ftbq.editor.domain;

import java.util.Objects;

/**
 * Requires the player to visit a specific location.
 */
public record LocationTask(String dimension, double x, double y, double z, double radius) implements Task {

    public LocationTask {
        Objects.requireNonNull(dimension, "dimension");
    }

    public LocationTask(double x, double y, double z, String dimension) {
        this(dimension, x, y, z, 5.0);
    }

    @Override
    public String type() {
        return "location";
    }

    @Override
    public String describe() {
        return "Visit %s at (%.1f, %.1f, %.1f) r=%.1f".formatted(dimension, x, y, z, radius);
    }
}
