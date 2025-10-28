package dev.ftbq.editor.services.generator;

import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.LootTable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Final result from a generation run, including produced chapters and diagnostics.
 */
public final class GenerationResult {
    private final List<Chapter> chapters;
    private final List<LootTable> lootTables;
    private final List<GenerationLogEntry> logs;
    private final GenerationValidationReport validationReport;

    public GenerationResult(List<Chapter> chapters,
                            List<LootTable> lootTables,
                            List<GenerationLogEntry> logs,
                            GenerationValidationReport validationReport) {
        this.chapters = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(chapters, "chapters")));
        this.lootTables = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(lootTables, "lootTables")));
        this.logs = Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(logs, "logs")));
        this.validationReport = Objects.requireNonNull(validationReport, "validationReport");
    }

    public List<Chapter> chapters() {
        return chapters;
    }

    public List<LootTable> lootTables() {
        return lootTables;
    }

    public List<GenerationLogEntry> logs() {
        return logs;
    }

    public GenerationValidationReport validationReport() {
        return validationReport;
    }
}
