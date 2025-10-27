package dev.ftbq.editor.domain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Utility class for exporting and importing quests to and from JSON files.
 */
public final class QuestIO {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new Jdk8Module())
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT);

    private QuestIO() {
    }

    public static void exportQuests(List<Quest> quests, Path file) throws IOException {
        Objects.requireNonNull(quests, "quests");
        Objects.requireNonNull(file, "file");

        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        MAPPER.writeValue(file.toFile(), quests);
    }

    public static List<Quest> importQuests(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        return MAPPER.readValue(file.toFile(), new TypeReference<List<Quest>>() { });
    }
}
