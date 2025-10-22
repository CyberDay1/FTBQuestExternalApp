package dev.ftbq.editor.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.ftbq.editor.domain.QuestFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class QuestFileJson {
    private static final String FILE_NAME = "quest-file.json";

    private QuestFileJson() {
    }

    public static QuestFile load(Path root) throws IOException {
        Objects.requireNonNull(root, "root");
        Path jsonPath = root.resolve(FILE_NAME);
        QuestFileData data = JsonConfig.OBJECT_MAPPER.readValue(jsonPath.toFile(), QuestFileData.class);
        return new QuestFile(data.id(), data.title(), List.of(), List.of());
    }

    public static void save(QuestFile questFile, Path root) throws IOException {
        Objects.requireNonNull(questFile, "questFile");
        Objects.requireNonNull(root, "root");
        Path jsonPath = root.resolve(FILE_NAME);
        Files.createDirectories(jsonPath.getParent());
        QuestFileData data = new QuestFileData(questFile.id(), questFile.title());
        JsonConfig.OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), data);
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record QuestFileData(@JsonProperty("id") String id,
                                 @JsonProperty("title") String title) {
    }
}
