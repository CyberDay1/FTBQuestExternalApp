package dev.ftbq.editor.io;

import dev.ftbq.editor.domain.QuestFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestFileJsonTest {
    @TempDir
    Path tempDir;

    @Test
    void roundTripPersistsIdAndTitle() throws IOException {
        QuestFile questFile = new QuestFile("quest-id", "Quest Title", List.of(), List.of());

        QuestFileJson.save(questFile, tempDir);
        QuestFile loaded = QuestFileJson.load(tempDir);

        assertEquals(questFile, loaded);
    }
}
