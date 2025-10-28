package dev.ftbq.editor.services.generator;

import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.io.snbt.SnbtQuestMapper;
import dev.ftbq.editor.importer.snbt.parser.SnbtParseException;
import dev.ftbq.editor.services.mods.RegisteredMod;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Coordinates end-to-end generation of quest chapters using an AI provider.
 */
public final class QuestChapterGenerator {

    private static final Pattern LEADING_KEY_PATTERN = Pattern.compile("(?m)^(\\s*)([A-Za-z0-9_\\-]+):");
    private static final Pattern INLINE_KEY_PATTERN = Pattern.compile("(?<=\\{|,)(\\s*)([A-Za-z0-9_\\-]+):");

    private final AiModelProvider modelProvider;
    private final SnbtQuestMapper snbtMapper;
    private final PromptAssembler promptAssembler;
    private final GeneratedContentValidator contentValidator;
    private final ChapterDraftWriter draftWriter;

    public QuestChapterGenerator(AiModelProvider modelProvider) {
        this(modelProvider, new SnbtQuestMapper(), new PromptAssembler(), new GeneratedContentValidator());
    }

    public QuestChapterGenerator(AiModelProvider modelProvider,
                                 SnbtQuestMapper snbtMapper,
                                 PromptAssembler promptAssembler,
                                 GeneratedContentValidator contentValidator) {
        this.modelProvider = Objects.requireNonNull(modelProvider, "modelProvider");
        this.snbtMapper = Objects.requireNonNull(snbtMapper, "snbtMapper");
        this.promptAssembler = Objects.requireNonNull(promptAssembler, "promptAssembler");
        this.contentValidator = Objects.requireNonNull(contentValidator, "contentValidator");
        this.draftWriter = new ChapterDraftWriter(snbtMapper);
    }

    public GenerationResult generate(QuestFile questFile,
                                     QuestDesignSpec designSpec,
                                     ModIntent modIntent,
                                     List<RegisteredMod> selectedMods,
                                     List<Path> exampleChapterPaths,
                                     Path draftsDirectory) throws IOException {
        Objects.requireNonNull(questFile, "questFile");
        Objects.requireNonNull(designSpec, "designSpec");
        Objects.requireNonNull(modIntent, "modIntent");
        Objects.requireNonNull(selectedMods, "selectedMods");
        Objects.requireNonNull(exampleChapterPaths, "exampleChapterPaths");
        Objects.requireNonNull(draftsDirectory, "draftsDirectory");

        List<GenerationLogEntry> logs = new ArrayList<>();
        GenerationContext context = buildContext(questFile, designSpec, modIntent, selectedMods, exampleChapterPaths, logs);
        ModelPrompt prompt = promptAssembler.buildPrompt(context);
        logs.add(GenerationLogEntry.of("prompt", promptAssembler.describe(prompt)));

        ModelResponse response = modelProvider.generate(prompt);
        String preview = response.content().length() > 200
                ? response.content().substring(0, 200) + "..."
                : response.content();
        logs.add(GenerationLogEntry.of("model", "Received model output (first 200 chars): " + preview.replace('\n', ' ')));

        List<Chapter> normalizedChapters;
        try {
            normalizedChapters = parseAndNormalize(response.content(), questFile, logs);
        } catch (IllegalStateException ex) {
            String reason = extractParseReason(ex);
            logs.add(GenerationLogEntry.of("postprocess",
                    "Parse failure detected; retrying with corrective nudge: " + reason));

            ModelPrompt retryPrompt = promptAssembler.buildPromptWithNudge(context, reason);
            logs.add(GenerationLogEntry.of("prompt", "retry → " + promptAssembler.describe(retryPrompt)));

            ModelResponse retryResponse = modelProvider.generate(retryPrompt);
            String retryPreview = retryResponse.content().length() > 200
                    ? retryResponse.content().substring(0, 200) + "..."
                    : retryResponse.content();
            logs.add(GenerationLogEntry.of("model", "Retry output (first 200 chars): " + retryPreview.replace('\n', ' ')));

            normalizedChapters = parseAndNormalize(retryResponse.content(), questFile, logs);
        }
        GenerationValidationReport validationReport = contentValidator.validate(normalizedChapters, designSpec, context);
        logs.add(GenerationLogEntry.of("validation",
                "Validation produced " + validationReport.issues().size() + " issue(s)."));

        Path draftPath = draftWriter.writeDraft(draftsDirectory, normalizedChapters, designSpec, modIntent);
        logs.add(GenerationLogEntry.of("draft", "Wrote draft SNBT to " + draftPath));

        return new GenerationResult(normalizedChapters, logs, validationReport);
    }

    private GenerationContext buildContext(QuestFile questFile,
                                           QuestDesignSpec designSpec,
                                           ModIntent modIntent,
                                           List<RegisteredMod> selectedMods,
                                           List<Path> exampleChapterPaths,
                                           List<GenerationLogEntry> logs) throws IOException {
        List<ExampleChapterConstraint> examples = new ArrayList<>();
        for (Path path : exampleChapterPaths) {
            if (!Files.exists(path)) {
                logs.add(GenerationLogEntry.of("context", "Example path missing: " + path));
                continue;
            }
            String snbt = Files.readString(path);
            try {
                QuestFile exampleFile = snbtMapper.fromSnbt(snbt);
                examples.add(new ExampleChapterConstraint(path, snbt, exampleFile.chapters()));
                logs.add(GenerationLogEntry.of("context",
                        "Loaded example chapters from " + path + " (" + exampleFile.chapters().size() + " chapter(s))"));
            } catch (SnbtParseException | IllegalArgumentException ex) {
                logs.add(GenerationLogEntry.of("context",
                        "Failed to parse example " + path + ": " + ex.getMessage()));
            }
        }

        Map<String, Set<String>> progressionMap = buildProgressionMap(questFile);
        return new GenerationContext(questFile, designSpec, modIntent, examples, progressionMap, selectedMods);
    }

    private Map<String, Set<String>> buildProgressionMap(QuestFile questFile) {
        Map<String, Set<String>> progressionMap = new LinkedHashMap<>();
        questFile.chapters().forEach(chapter ->
                chapter.quests().forEach(quest -> {
                    String node = chapter.id() + "::" + quest.id() + " (" + quest.title() + ")";
                    Set<String> dependencies = quest.dependencies().stream()
                            .map(Dependency::questId)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    progressionMap.put(node, dependencies);
                }));
        return progressionMap;
    }

    private List<Chapter> parseAndNormalize(String snbt,
                                            QuestFile questFile,
                                            List<GenerationLogEntry> logs) {
        QuestFile generatedFile;
        try {
            generatedFile = snbtMapper.fromSnbt(snbt);
        } catch (SnbtParseException | IllegalArgumentException ex) {
            String sanitized = sanitizeUnquotedKeys(snbt);
            if (!sanitized.equals(snbt)) {
                try {
                    generatedFile = snbtMapper.fromSnbt(sanitized);
                    logs.add(GenerationLogEntry.of("postprocess", "Recovered from parse failure by quoting unquoted keys."));
                } catch (SnbtParseException | IllegalArgumentException inner) {
                    logs.add(GenerationLogEntry.of("postprocess", "Failed to parse model SNBT after sanitizing: " + inner.getMessage()));
                    throw new IllegalStateException("Model output was not valid SNBT", inner);
                }
            } else {
                logs.add(GenerationLogEntry.of("postprocess", "Failed to parse model SNBT: " + ex.getMessage()));
                throw new IllegalStateException("Model output was not valid SNBT", ex);
            }
        }
        List<Chapter> normalized = normalizeChapters(generatedFile.chapters(), questFile);
        logs.add(GenerationLogEntry.of("postprocess",
                "Parsed and normalized " + normalized.size() + " chapter(s) from model output."));
        return normalized;
    }

    private String extractParseReason(IllegalStateException ex) {
        if (ex.getCause() != null && ex.getCause().getMessage() != null && !ex.getCause().getMessage().isBlank()) {
            return ex.getCause().getMessage();
        }
        if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            return ex.getMessage();
        }
        return "SNBT parse failure";
    }

    private String sanitizeUnquotedKeys(String snbt) {
        String withLeading = LEADING_KEY_PATTERN.matcher(snbt).replaceAll("$1\"$2\":");
        return INLINE_KEY_PATTERN.matcher(withLeading).replaceAll("$1\"$2\":");
    }

    private List<Chapter> normalizeChapters(List<Chapter> chapters, QuestFile existing) {
        Set<String> usedChapterIds = existing.chapters().stream()
                .map(Chapter::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> usedQuestIds = existing.chapters().stream()
                .flatMap(chapter -> chapter.quests().stream())
                .map(Quest::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Chapter> normalized = new ArrayList<>();
        for (Chapter chapter : chapters) {
            String normalizedChapterId = normalizeId(chapter.id(), chapter.title(), "chapter", usedChapterIds);
            Chapter.Builder chapterBuilder = Chapter.builder()
                    .id(normalizedChapterId)
                    .title(chapter.title().isBlank() ? "Untitled Chapter" : chapter.title().trim())
                    .icon(chapter.icon())
                    .background(chapter.background())
                    .visibility(chapter.visibility());

            List<Quest> quests = new ArrayList<>(chapter.quests());
            quests.sort(Comparator.comparing(Quest::title, String.CASE_INSENSITIVE_ORDER));

            Map<String, String> questIdRemap = new LinkedHashMap<>();
            for (Quest quest : quests) {
                String newId = normalizeId(quest.id(), quest.title(), "quest", usedQuestIds);
                questIdRemap.put(quest.id(), newId);
            }

            for (Quest quest : quests) {
                String normalizedQuestId = questIdRemap.getOrDefault(quest.id(), quest.id());
                Quest.Builder questBuilder = Quest.builder()
                        .id(normalizedQuestId)
                        .title(quest.title().isBlank() ? "Quest " + normalizedQuestId : quest.title().trim())
                        .description(quest.description().trim())
                        .icon(quest.icon())
                        .visibility(quest.visibility())
                        .tasks(new ArrayList<>(quest.tasks()))
                        .rewards(new ArrayList<>(quest.rewards()));

                List<Dependency> normalizedDependencies = quest.dependencies().stream()
                        .map(dependency -> new Dependency(
                                questIdRemap.getOrDefault(dependency.questId(), dependency.questId()),
                                dependency.required()))
                        .collect(Collectors.toCollection(ArrayList::new));
                questBuilder.dependencies(normalizedDependencies);
                chapterBuilder.addQuest(questBuilder.build());
            }
            normalized.add(chapterBuilder.build());
        }
        return normalized;
    }

    private String normalizeId(String candidate,
                               String title,
                               String prefix,
                               Set<String> usedIds) {
        String base = candidate;
        if (base == null || base.isBlank()) {
            base = title;
        }
        if (base == null || base.isBlank()) {
            base = prefix + "-" + UUID.randomUUID();
        }
        String normalized = base.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_]+", "-")
                .replaceAll("^-+", "")
                .replaceAll("-+$", "");
        if (normalized.isBlank()) {
            normalized = prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        }
        String result = normalized;
        int counter = 2;
        while (usedIds.contains(result)) {
            result = normalized + '-' + counter++;
        }
        usedIds.add(result);
        return result;
    }
}
