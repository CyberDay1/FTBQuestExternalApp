package dev.ftbq.editor.services.io;

import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.io.snbt.SnbtIo;
import dev.ftbq.editor.io.snbt.SnbtQuestMapper;
import dev.ftbq.editor.importer.snbt.model.ImportOptions;
import dev.ftbq.editor.importer.snbt.model.ImportedQuestPack;
import dev.ftbq.editor.importer.snbt.model.QuestImportResult;
import dev.ftbq.editor.importer.snbt.service.SnbtQuestImporter;
import java.io.File;

/**
 * Coordinates SNBT-based import and export of quest packs.
 */
public class SnbtImportExportService {

    private final SnbtQuestMapper mapper = new SnbtQuestMapper();
    private final SnbtQuestImporter importer = new SnbtQuestImporter();

    public void exportPack(QuestFile file, File directory) throws Exception {
        var snbt = mapper.toSnbt(file);
        SnbtIo.write(new File(directory, "questbook/data.snbt"), snbt);
    }

    public ImportedQuestPack previewPack(File directory) throws Exception {
        var snbt = SnbtIo.read(new File(directory, "questbook/data.snbt"));
        return importer.parse(snbt);
    }

    public QuestImportResult importPack(File directory, QuestFile current, ImportOptions options) throws Exception {
        var pack = previewPack(directory);
        return importer.merge(current, pack, options);
    }

    public QuestFile importPack(File directory) throws Exception {
        var pack = previewPack(directory);
        var baseFile = QuestFile.builder()
                .id(pack.id())
                .title(pack.title())
                .build();
        var options = ImportOptions.builder()
                .copyAssets(false)
                .build();
        return importer.merge(baseFile, pack, options).questFile();
    }
}
