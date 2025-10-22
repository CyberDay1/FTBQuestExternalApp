package dev.ftbq.editor.domain;

import java.util.Objects;

/**
 * Requires the player to visit a specific location.
 */
public record LocationTask(String dimension, double x, double y, double z, double radius) implements Task {

    public LocationTask {
        Objects.requireNonNull(dimension, "dimension");
    }

    @Override
    public String type() {
        return "location";
    }
}
