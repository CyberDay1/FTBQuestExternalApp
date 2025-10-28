package dev.ftbq.editor.io.snbt;

import dev.ftbq.editor.domain.QuestFile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Bridges AI generated SNBT responses with the quest import pipeline.
 */
public final class AiQuestBridge {

    private final SnbtQuestMapper mapper;
    private final SnbtFormatter formatter;
    private SnbtFormatter.FormatResult lastResult;

    public AiQuestBridge() {
        this(new SnbtQuestMapper());
    }

    public AiQuestBridge(SnbtQuestMapper mapper) {
        this(mapper, new SnbtFormatter(mapper));
    }

    public AiQuestBridge(SnbtQuestMapper mapper, SnbtFormatter formatter) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
    }

    public QuestFile parse(String snbtText) {
        Objects.requireNonNull(snbtText, "snbtText");
        SnbtFormatter.FormatResult result = formatter.format(snbtText);
        lastResult = result;
        return result.questFile();
    }

    public Path saveDraft(Path rootDirectory) throws IOException {
        Objects.requireNonNull(rootDirectory, "rootDirectory");
        SnbtFormatter.FormatResult result = lastResult;
        if (result == null) {
            throw new IllegalStateException("No SNBT draft is available to save.");
        }

        Path draftsDirectory = rootDirectory.resolve("drafts");
        Files.createDirectories(draftsDirectory);
        Path draftFile = draftsDirectory.resolve(nextDraftFileName());
        Files.writeString(draftFile, result.formattedText(), StandardCharsets.UTF_8);
        return draftFile;
    }

    public Optional<String> lastFormattedText() {
        return Optional.ofNullable(lastResult).map(SnbtFormatter.FormatResult::formattedText);
    }

    private String nextDraftFileName() {
        return "draft-" + UUID.randomUUID() + ".snbt";
    }
}
