package dev.ftbq.editor.io.snbt;

import dev.ftbq.editor.domain.QuestFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Imports quest data from SNBT sources.
 */
public final class SnbtImporter {

    private final SnbtFormatter formatter;

    public SnbtImporter() {
        this(new SnbtFormatter());
    }

    public SnbtImporter(SnbtFormatter formatter) {
        this.formatter = Objects.requireNonNull(formatter, "formatter");
    }

    public QuestFile importFromFile(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        String snbtText = Files.readString(source, StandardCharsets.UTF_8);
        return formatter.format(snbtText).questFile();
    }
}
