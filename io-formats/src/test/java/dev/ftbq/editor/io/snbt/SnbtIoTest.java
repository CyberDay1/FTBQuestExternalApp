package dev.ftbq.editor.io.snbt;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SnbtIoTest {

    @TempDir
    Path tempDir;

    @Test
    void writesAndReadsUtf8Content() throws Exception {
        var file = tempDir.resolve("questbook/data.snbt").toFile();
        var content = "{id:\"pack\"}";

        SnbtIo.write(file, content);

        assertEquals(content, SnbtIo.read(file));
    }
}
