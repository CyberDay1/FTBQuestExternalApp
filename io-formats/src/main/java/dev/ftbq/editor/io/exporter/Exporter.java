package dev.ftbq.editor.io.exporter;

import dev.ftbq.editor.domain.BackgroundRef;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.ChapterGroup;
import dev.ftbq.editor.domain.IconRef;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Exporter {
    private static final Logger LOGGER = LoggerFactory.getLogger(Exporter.class);

    public void exportPack(QuestFile questFile, Path packRoot, Path targetRoot) throws IOException {
        Objects.requireNonNull(questFile, "questFile");
        Objects.requireNonNull(packRoot, "packRoot");
        Objects.requireNonNull(targetRoot, "targetRoot");

        long start = System.currentTimeMillis();
        LOGGER.info("Exporting quest pack | questId={} targetRoot={}", questFile.id(), targetRoot);

        String namespace = extractNamespace(questFile.id());

        Path dataNamespaceRoot = targetRoot.resolve("data").resolve(namespace);
        Path ftbquestsRoot = dataNamespaceRoot.resolve("ftbquests");
        QuestFileJson.save(questFile, ftbquestsRoot);

        Path lootTableRoot = dataNamespaceRoot.resolve("loot_tables");
        for (LootTable lootTable : questFile.lootTables()) {
            LootTableJson.save(lootTable, lootTableRoot);
        }

        AssetCopySummary summary = copyAssets(questFile, packRoot, targetRoot.resolve("assets").resolve(namespace));
        long duration = System.currentTimeMillis() - start;
        LOGGER.info(
                "Quest pack export complete | questId={} lootTables={} icons={} backgrounds={} durationMs={} targetRoot={}",
                questFile.id(),
                questFile.lootTables().size(),
                summary.icons(),
                summary.backgrounds(),
                duration,
                targetRoot);
    }

    private static String extractNamespace(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(0, colon) : id;
    }

    private static AssetCopySummary copyAssets(QuestFile questFile, Path packRoot, Path assetsRoot) throws IOException {
        Set<Path> iconPaths = new HashSet<>();
        Set<Path> backgroundPaths = new HashSet<>();

        for (ChapterGroup group : questFile.chapterGroups()) {
            collectIconPath(iconPaths, group.icon(), packRoot);
        }

        for (Chapter chapter : questFile.chapters()) {
            collectIconPath(iconPaths, chapter.icon(), packRoot);
            collectBackgroundPath(backgroundPaths, chapter.background(), packRoot);
            for (Quest quest : chapter.quests()) {
                collectIconPath(iconPaths, quest.icon(), packRoot);
            }
        }

        int iconsCopied = copyFiles(iconPaths, assetsRoot.resolve("ftbquests").resolve("icons"));
        int backgroundsCopied = copyFiles(backgroundPaths, assetsRoot.resolve("ftbquests").resolve("backgrounds"));
        LOGGER.info("Asset export summary | icons={} backgrounds={} targetRoot={}", iconsCopied, backgroundsCopied, assetsRoot);
        return new AssetCopySummary(iconsCopied, backgroundsCopied);
    }

    private static void collectIconPath(Set<Path> iconPaths, IconRef icon, Path packRoot) throws IOException {
        Optional<Path> resolved = resolveRelativeFile(icon.relativePath(), packRoot, "icon");
        if (resolved.isPresent()) {
            iconPaths.add(resolved.get());
            return;
        }
        toExistingFile(icon.icon()).ifPresent(iconPaths::add);
    }

    private static void collectBackgroundPath(Set<Path> backgroundPaths, BackgroundRef background, Path packRoot) throws IOException {
        Optional<Path> resolved = resolveRelativeFile(background.relativePath(), packRoot, "background");
        if (resolved.isPresent()) {
            backgroundPaths.add(resolved.get());
            return;
        }
        toExistingFile(background.texture()).ifPresent(backgroundPaths::add);
    }

    private static int copyFiles(Set<Path> sources, Path targetRoot) throws IOException {
        if (sources.isEmpty()) {
            return 0;
        }
        Files.createDirectories(targetRoot);
        int copied = 0;
        for (Path source : sources) {
            if (Files.isRegularFile(source)) {
                Path target = targetRoot.resolve(source.getFileName().toString());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                copied++;
            }
        }
        return copied;
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

    private static Optional<Path> resolveRelativeFile(Optional<String> relativePath, Path packRoot, String assetType) throws IOException {
        if (relativePath == null || relativePath.isEmpty()) {
            return Optional.empty();
        }
        String relative = relativePath.orElseThrow();
        Path normalizedRoot = packRoot.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(relative).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new IOException("Relative " + assetType + " path escapes pack root: " + relative);
        }
        if (!Files.exists(resolved) || !Files.isRegularFile(resolved)) {
            throw new IOException("Missing " + assetType + " asset at relative path: " + relative);
        }
        return Optional.of(resolved);
    }

    private record AssetCopySummary(int icons, int backgrounds) {
    }
}
