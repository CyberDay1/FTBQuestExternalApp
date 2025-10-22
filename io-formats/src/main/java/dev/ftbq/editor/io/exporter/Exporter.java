package dev.ftbq.editor.io.exporter;

import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.ChapterGroup;
import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.io.LootTableJson;
import dev.ftbq.editor.io.QuestFileJson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class Exporter {
    public void exportPack(QuestFile questFile, Path targetRoot) throws IOException {
        Objects.requireNonNull(questFile, "questFile");
        Objects.requireNonNull(targetRoot, "targetRoot");
        String namespace = extractNamespace(questFile.id());

        Path dataNamespaceRoot = targetRoot.resolve("data").resolve(namespace);
        Path ftbquestsRoot = dataNamespaceRoot.resolve("ftbquests");
        QuestFileJson.save(questFile, ftbquestsRoot);

        Path lootTableRoot = dataNamespaceRoot.resolve("loot_tables");
        for (LootTable lootTable : questFile.lootTables()) {
            LootTableJson.save(lootTable, lootTableRoot);
        }

        copyAssets(questFile, targetRoot.resolve("assets").resolve(namespace));
    }

    private static String extractNamespace(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(0, colon) : id;
    }

    private static void copyAssets(QuestFile questFile, Path assetsRoot) throws IOException {
        Set<Path> iconPaths = new HashSet<>();
        Set<Path> backgroundPaths = new HashSet<>();

        for (ChapterGroup group : questFile.chapterGroups()) {
            toExistingFile(group.icon().icon()).ifPresent(iconPaths::add);
        }

        for (Chapter chapter : questFile.chapters()) {
            toExistingFile(chapter.icon().icon()).ifPresent(iconPaths::add);
            toExistingFile(chapter.background().texture()).ifPresent(backgroundPaths::add);
            for (Quest quest : chapter.quests()) {
                toExistingFile(quest.icon().icon()).ifPresent(iconPaths::add);
            }
        }

        copyFiles(iconPaths, assetsRoot.resolve("ftbquests").resolve("icons"));
        copyFiles(backgroundPaths, assetsRoot.resolve("ftbquests").resolve("backgrounds"));
    }

    private static void copyFiles(Set<Path> sources, Path targetRoot) throws IOException {
        if (sources.isEmpty()) {
            return;
        }
        Files.createDirectories(targetRoot);
        for (Path source : sources) {
            if (Files.isRegularFile(source)) {
                Path target = targetRoot.resolve(source.getFileName().toString());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Optional<Path> toExistingFile(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            Path path = Path.of(value);
            if (Files.exists(path) && Files.isRegularFile(path)) {
                return Optional.of(path);
            }
        } catch (InvalidPathException ignored) {
            // not a local file reference
        }
        return Optional.empty();
    }
}
