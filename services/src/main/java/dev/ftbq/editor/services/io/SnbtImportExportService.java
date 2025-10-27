package dev.ftbq.editor.services.io;

import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.io.snbt.SnbtIo;
import dev.ftbq.editor.io.snbt.SnbtQuestMapper;
import java.io.File;

/**
 * Coordinates SNBT-based import and export of quest packs.
 */
public class SnbtImportExportService {

    private final SnbtQuestMapper mapper = new SnbtQuestMapper();

    public void exportPack(QuestFile file, File directory) throws Exception {
        var snbt = mapper.toSnbt(file);
        SnbtIo.write(new File(directory, "questbook/data.snbt"), snbt);
    }

    public QuestFile importPack(File directory) throws Exception {
        var snbt = SnbtIo.read(new File(directory, "questbook/data.snbt"));
        return mapper.fromSnbt(snbt);
    }
}
