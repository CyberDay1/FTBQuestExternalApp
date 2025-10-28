package dev.ftbq.editor.services.generator;

import dev.ftbq.editor.domain.Chapter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * User supplied example chapters parsed from SNBT to guide the model.
 */
public final class ExampleChapterConstraint {
    private final Path source;
    private final String snbt;
    private final List<Chapter> chapters;

    public ExampleChapterConstraint(Path source, String snbt, List<Chapter> chapters) {
        this.source = Objects.requireNonNull(source, "source");
        this.snbt = Objects.requireNonNull(snbt, "snbt");
        this.chapters = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(chapters, "chapters")));
    }

    public Path source() {
        return source;
    }

    public String snbt() {
        return snbt;
    }

    public List<Chapter> chapters() {
        return chapters;
    }
}
