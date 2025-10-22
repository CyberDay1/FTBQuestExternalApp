package dev.ftbq.editor.domain.version;

import java.util.Arrays;

/**
 * Represents supported Minecraft base versions for the editor.
 */
public enum MinecraftVersion {
    V1_20_1("1.20.1"),
    V1_20_2("1.20.2"),
    V1_20_6("1.20.6"),
    V1_21("1.21"),
    V1_21_1("1.21.1"),
    V1_21_2("1.21.2"),
    V1_21_3("1.21.3"),
    V1_21_4("1.21.4"),
    V1_21_5("1.21.5"),
    V1_21_6("1.21.6"),
    V1_21_7("1.21.7"),
    V1_21_8("1.21.8"),
    V1_21_9("1.21.9"),
    V1_21_10("1.21.10");

    private final String id;

    MinecraftVersion(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    @Override
    public String toString() {
        return id;
    }

    public static MinecraftVersion fromId(String id) {
        return Arrays.stream(values())
            .filter(version -> version.id.equals(id))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Unknown Minecraft version: " + id));
    }
}
