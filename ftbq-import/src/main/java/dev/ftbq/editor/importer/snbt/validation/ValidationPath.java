package dev.ftbq.editor.importer.snbt.validation;

import java.util.Objects;

/**
 * Represents a dotted path pointing to a location inside the SNBT structure.
 */
final class ValidationPath {
    private static final String ROOT = "$";

    private final String value;

    private ValidationPath(String value) {
        this.value = Objects.requireNonNull(value, "value");
    }

    static ValidationPath root() {
        return new ValidationPath(ROOT);
    }

    ValidationPath property(String name) {
        Objects.requireNonNull(name, "name");
        if (value.equals(ROOT)) {
            return new ValidationPath(value + "." + name);
        }
        return new ValidationPath(value + "." + name);
    }

    ValidationPath index(int index) {
        return new ValidationPath(value + "[" + index + "]");
    }

    @Override
    public String toString() {
        return value;
    }
}
