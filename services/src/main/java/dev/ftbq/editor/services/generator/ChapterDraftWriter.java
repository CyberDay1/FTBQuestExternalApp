package dev.ftbq.editor.services.generator;

import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.io.snbt.SnbtQuestMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Persists generated chapters as SNBT drafts for manual review.
 */
public final class ChapterDraftWriter {

    private final SnbtQuestMapper mapper;

    public ChapterDraftWriter(SnbtQuestMapper mapper) {
        this.mapper = mapper;
    }

    public Path writeDraft(Path directory,
                           List<Chapter> chapters,
                           QuestDesignSpec spec,
                           ModIntent intent) throws IOException {
        Files.createDirectories(directory);
        String suffix = DateTimeFormatter.ofPattern("yyyyMMddHHmmss", Locale.ROOT)
                .withZone(ZoneOffset.UTC)
                .format(Instant.now());
        String modSegment = intent.modId().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "-");
        if (modSegment.isBlank()) {
            modSegment = "mod";
        }
        Path target = directory.resolve("ai-chapter-draft-" + modSegment + "-" + suffix + ".snbt");
        QuestFile file = QuestFile.builder()
                .id("draft-" + modSegment)
                .title("Draft for " + intent.modId() + " (" + spec.theme() + ")")
                .chapterGroups(List.of())
                .chapters(chapters)
                .lootTables(List.of())
                .build();
        String snbt = mapper.toSnbt(file);
        Files.writeString(target, snbt);
        return target;
    }
}
