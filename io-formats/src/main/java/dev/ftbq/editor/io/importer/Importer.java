package dev.ftbq.editor.io.importer;

import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.io.LootTableJson;
import dev.ftbq.editor.io.QuestFileJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

public final class Importer {
    public QuestFile importPack(Path sourceRoot) throws IOException {
        Objects.requireNonNull(sourceRoot, "sourceRoot");
        Path dataRoot = sourceRoot.resolve("data");
        String namespace = findNamespace(dataRoot);
        Path namespaceRoot = dataRoot.resolve(namespace);

        QuestFile questFile = QuestFileJson.load(namespaceRoot.resolve("ftbquests"));
        List<LootTable> lootTables = LootTableJson.loadAll(namespaceRoot.resolve("loot_tables"));
        if (lootTables.isEmpty()) {
            lootTables = questFile.lootTables();
        }

        return QuestFile.builder()
                .id(questFile.id())
                .title(questFile.title())
                .chapterGroups(questFile.chapterGroups())
                .chapters(questFile.chapters())
                .lootTables(lootTables)
                .build();
    }

    private static String findNamespace(Path dataRoot) throws IOException {
        if (!Files.exists(dataRoot)) {
            throw new IOException("Missing data directory: " + dataRoot);
        }
        try (var stream = Files.list(dataRoot)) {
            return stream
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .findFirst()
                    .orElseThrow(() -> new IOException("No namespace found under " + dataRoot));
        }
    }
}
