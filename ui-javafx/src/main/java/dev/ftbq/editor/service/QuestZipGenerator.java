package dev.ftbq.editor.service;

import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.io.snbt.SnbtLangBuilder;
import dev.ftbq.editor.io.snbt.SnbtQuestMapper;
import dev.ftbq.editor.io.snbt.SnbtQuestMapper.Fragments;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds a modpack-ready ftbquests.zip archive from the in-memory quest file.
 */
public final class QuestZipGenerator {

    private final SnbtQuestMapper mapper;
    private final SnbtLangBuilder langBuilder;

    public QuestZipGenerator() {
        this(new SnbtQuestMapper(), new SnbtLangBuilder());
    }

    public QuestZipGenerator(SnbtQuestMapper mapper, SnbtLangBuilder langBuilder) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.langBuilder = Objects.requireNonNull(langBuilder, "langBuilder");
    }

    public Path generate(QuestFile questFile,
                         Path workspace,
                         UserSettings.EditorSettings settings) throws IOException {
        Objects.requireNonNull(questFile, "questFile");
        Objects.requireNonNull(settings, "settings");

        Path tempRoot = Files.createTempDirectory("ftbq-quests-");
        Path baseDir = tempRoot.resolve(UUID.randomUUID().toString());
        Path questsRoot = baseDir.resolve("quests");
        Files.createDirectories(questsRoot);

        Fragments fragments = mapper.fragmentsFor(questFile);
        writeString(questsRoot.resolve("data.snbt"), fragments.data());
        writeString(questsRoot.resolve("chapter_groups.snbt"), fragments.chapterGroups());

        Path chaptersDir = questsRoot.resolve("chapters");
        Files.createDirectories(chaptersDir);
        for (Map.Entry<Chapter, String> entry : fragments.chapters().entrySet()) {
            String fileName = sanitize(entry.getKey().id(), entry.getKey().title()) + ".snbt";
            writeString(chaptersDir.resolve(fileName), entry.getValue());
        }

        Path rewardDir = questsRoot.resolve("reward_tables");
        Files.createDirectories(rewardDir);
        for (Map.Entry<LootTable, String> entry : fragments.rewardTables().entrySet()) {
            String fileName = sanitize(entry.getKey().id(), "reward_table") + ".snbt";
            writeString(rewardDir.resolve(fileName), entry.getValue());
        }

        Path langDir = questsRoot.resolve("lang");
        Files.createDirectories(langDir);
        populateLanguages(langDir, questFile, workspace, settings);

        Path zipFile = tempRoot.resolve("ftbquests.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            Path base = baseDir;
            try (var paths = Files.walk(base)) {
                paths.forEach(path -> {
                        try {
                            if (Files.isDirectory(path)) {
                                if (path.equals(base)) {
                                    return;
                                }
                                String entryName = toZipEntryName(base, path) + "/";
                                zip.putNextEntry(new ZipEntry(entryName));
                                zip.closeEntry();
                            } else {
                                String entryName = toZipEntryName(base, path);
                                zip.putNextEntry(new ZipEntry(entryName));
                                try (InputStream in = Files.newInputStream(path)) {
                                    in.transferTo(zip);
                                }
                                zip.closeEntry();
                            }
                        } catch (IOException ex) {
                            throw new RuntimeException(ex);
                        }
                });
            }
        } catch (RuntimeException ex) {
            if (ex.getCause() instanceof IOException io) {
                throw io;
            }
            throw ex;
        }
        return zipFile;
    }

    private void populateLanguages(Path langDir,
                                   QuestFile questFile,
                                   Path workspace,
                                   UserSettings.EditorSettings settings) throws IOException {
        Path existingLangDir = workspace != null ? workspace.resolve("questbook").resolve("lang") : null;
        Path enSource = existingLangDir != null ? existingLangDir.resolve("en_us.snbt") : null;
        Path zhSource = existingLangDir != null ? existingLangDir.resolve("zh_cn.snbt") : null;

        Path enTarget = langDir.resolve("en_us.snbt");
        if (enSource != null && Files.exists(enSource)) {
            copyFile(enSource, enTarget);
        } else {
            writeString(enTarget, langBuilder.buildEnUs(questFile));
        }

        Path zhTarget = langDir.resolve("zh_cn.snbt");
        if (zhSource != null && Files.exists(zhSource)) {
            copyFile(zhSource, zhTarget);
        } else {
            String zhContent = langBuilder.buildStub(questFile, false);
            writeString(zhTarget, zhContent);
        }
    }

    private void writeString(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }

    private void copyFile(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private String sanitize(String id, String fallback) {
        String value = (id == null || id.isBlank()) ? fallback : id;
        if (value == null || value.isBlank()) {
            value = "entry";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_') {
                builder.append(Character.toLowerCase(c));
            } else {
                builder.append('_');
            }
        }
        return builder.toString();
    }

    private String toZipEntryName(Path base, Path path) {
        Path relative = base.relativize(path);
        return relative.toString().replace('\\', '/');
    }
}
