package dev.ftbq.editor.importer.snbt.service;

import dev.ftbq.editor.domain.AdvancementTask;
import dev.ftbq.editor.domain.BackgroundRef;
import dev.ftbq.editor.domain.BiomeTask;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.ChapterGroup;
import dev.ftbq.editor.domain.ChapterImage;
import dev.ftbq.editor.domain.CheckmarkTask;
import dev.ftbq.editor.domain.CustomTask;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.DimensionTask;
import dev.ftbq.editor.domain.FluidTask;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.ItemTask;
import dev.ftbq.editor.domain.KillTask;
import dev.ftbq.editor.domain.LocationTask;
import dev.ftbq.editor.domain.ObservationTask;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.domain.QuestLink;
import dev.ftbq.editor.domain.QuestShape;
import dev.ftbq.editor.domain.Reward;
import dev.ftbq.editor.domain.RewardCommand;
import dev.ftbq.editor.domain.StageTask;
import dev.ftbq.editor.domain.StatTask;
import dev.ftbq.editor.domain.StructureTask;
import dev.ftbq.editor.domain.Task;
import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.domain.XpTask;
import dev.ftbq.editor.importer.snbt.model.ImportOptions;
import dev.ftbq.editor.importer.snbt.model.ImportConflictPolicy;
import dev.ftbq.editor.importer.snbt.model.ImportedChapter;
import dev.ftbq.editor.importer.snbt.model.ImportedChapterGroup;
import dev.ftbq.editor.importer.snbt.model.ImportedDependency;
import dev.ftbq.editor.importer.snbt.model.ImportedQuest;
import dev.ftbq.editor.importer.snbt.model.ImportedQuestPack;
import dev.ftbq.editor.importer.snbt.model.ImportedReward;
import dev.ftbq.editor.importer.snbt.model.ImportedTask;
import dev.ftbq.editor.importer.snbt.model.QuestImportResult;
import dev.ftbq.editor.importer.snbt.model.QuestImportSummary;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import dev.ftbq.editor.domain.HexId;

/**
 * Coordinates parsing SNBT quest data and merging it into an existing quest file.
 */
public final class SnbtQuestImporter {

    private final SnbtQuestPackReader reader = new SnbtQuestPackReader();

    public ImportedQuestPack parse(String snbtText) {
        return reader.read(snbtText);
    }

    public QuestImportResult merge(QuestFile current,
                                   ImportedQuestPack imported,
                                   ImportOptions options) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(imported, "imported");
        Objects.requireNonNull(options, "options");

        List<String> warnings = new ArrayList<>(imported.warnings());
        List<String> assetWarnings = new ArrayList<>();
        List<String> addedChapters = new ArrayList<>();
        List<String> mergedChapters = new ArrayList<>();
        List<String> addedQuests = new ArrayList<>();
        List<String> renamedIds = new ArrayList<>();
        Map<String, String> idRemap = new LinkedHashMap<>();

        Map<String, Quest> existingQuests = new LinkedHashMap<>();
        for (Chapter chapter : current.chapters()) {
            for (Quest quest : chapter.quests()) {
                existingQuests.put(quest.id(), quest);
            }
        }

        Map<String, GroupDescriptor> groupDescriptors = new LinkedHashMap<>();
        for (ChapterGroup group : current.chapterGroups()) {
            groupDescriptors.put(group.id(), new GroupDescriptor(group.title(), group.icon(), group.visibility(), new ArrayList<>(group.chapterIds())));
        }

        Map<String, String> groupIdRemap = new LinkedHashMap<>();
        for (ChapterGroup group : current.chapterGroups()) {
            groupIdRemap.put(group.id(), group.id());
        }

        if (options.targetGroupId() != null && !groupDescriptors.containsKey(options.targetGroupId())) {
            warnings.add("Target chapter group %s was not found; new chapters will use their original groups.".formatted(options.targetGroupId()));
        }

        // Prepare imported chapter groups when no override is specified
        if (options.targetGroupId() == null) {
            Set<String> usedGroupIds = new LinkedHashSet<>(groupDescriptors.keySet());
            for (ImportedChapterGroup group : imported.chapterGroups()) {
                String desiredId = normalizeId(group.id());
                boolean conflict = groupDescriptors.containsKey(desiredId);
                ResolutionResult resolution = resolveId(desiredId, conflict, options.chapterPolicy(), usedGroupIds, "chapter group", warnings, renamedIds, imported.id());
                if (resolution.type == ResolutionType.SKIP) {
                    continue;
                }
                groupIdRemap.put(group.id(), resolution.resolvedId);
                if (resolution.type == ResolutionType.NEW) {
                    groupDescriptors.put(resolution.resolvedId,
                            new GroupDescriptor(group.title(), new IconRef(group.icon()), group.visibility(), new ArrayList<>()));
                }
            }
        }

        // Ensure a fallback group exists
        String fallbackGroupId = determineFallbackGroup(groupDescriptors, warnings);

        List<Chapter> updatedChapters = new ArrayList<>(current.chapters());
        Map<String, Chapter> chapterIndex = new LinkedHashMap<>();
        for (Chapter chapter : updatedChapters) {
            chapterIndex.put(chapter.id(), chapter);
        }

        Set<String> usedChapterIds = new LinkedHashSet<>(chapterIndex.keySet());
        Set<String> usedQuestIds = new LinkedHashSet<>(existingQuests.keySet());

        for (ImportedChapter chapter : imported.chapters()) {
            String originalChapterId = normalizeId(chapter.id());
            boolean conflict = chapterIndex.containsKey(originalChapterId);
            ResolutionResult chapterResolution = resolveId(originalChapterId, conflict, options.chapterPolicy(), usedChapterIds, "chapter", warnings, renamedIds, imported.id());
            if (chapterResolution.type == ResolutionType.SKIP) {
                continue;
            }

            String targetGroupId = resolveGroupForChapter(chapter, options, groupIdRemap, groupDescriptors, fallbackGroupId, warnings);
            if (targetGroupId != null) {
                groupDescriptors.computeIfAbsent(targetGroupId,
                        id -> new GroupDescriptor("Group " + id, new IconRef("minecraft:book"), Visibility.VISIBLE, new ArrayList<>()))
                        .chapterIds().add(chapterResolution.resolvedId);
            }

            idRemap.put(originalChapterId, chapterResolution.resolvedId);

            Map<String, Quest> existingChapterQuests = new LinkedHashMap<>();
            if (chapterResolution.type == ResolutionType.MERGE) {
                Chapter existing = chapterIndex.get(originalChapterId);
                if (existing != null) {
                    for (Quest quest : existing.quests()) {
                        existingChapterQuests.put(quest.id(), quest);
                    }
                }
            }

            Chapter mergedChapter = applyChapterResolution(chapterResolution,
                    chapter,
                    existingChapterQuests,
                    usedQuestIds,
                    options.questPolicy(),
                    warnings,
                    addedQuests,
                    renamedIds,
                    idRemap,
                    mergedChapters,
                    addedChapters);

            if (chapterResolution.type == ResolutionType.MERGE) {
                replaceChapter(updatedChapters, mergedChapter);
                chapterIndex.put(mergedChapter.id(), mergedChapter);
            } else {
                updatedChapters.add(mergedChapter);
                chapterIndex.put(mergedChapter.id(), mergedChapter);
            }
        }

        List<ChapterGroup> updatedGroups = rebuildGroups(groupDescriptors);

        if (options.copyAssets() && options.assetSource() != null && options.assetDestination() != null) {
            copyAssets(imported.referencedAssets(), options.assetSource(), options.assetDestination(), assetWarnings);
        }

        QuestFile updatedFile = new QuestFile(current.id(), current.title(), updatedGroups, updatedChapters, current.lootTables());
        QuestImportSummary summary = new QuestImportSummary(addedChapters, mergedChapters, addedQuests, renamedIds, idRemap, warnings, assetWarnings);
        return new QuestImportResult(updatedFile, summary);
    }

    private void copyAssets(Set<String> assets,
                            Path sourceRoot,
                            Path destinationRoot,
                            List<String> warnings) {
        for (String asset : assets) {
            if (!looksLikeFileAsset(asset)) {
                continue;
            }
            Path source = sourceRoot.resolve(asset);
            Path destination = destinationRoot.resolve(asset);
            try {
                if (Files.exists(source)) {
                    Path parent = destination.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    warnings.add("Missing asset " + asset);
                }
            } catch (IOException e) {
                warnings.add("Failed to copy asset %s: %s".formatted(asset, e.getMessage()));
            }
        }
    }

    private boolean looksLikeFileAsset(String value) {
        return value.contains("/") || value.endsWith(".png") || value.endsWith(".jpg") || value.endsWith(".jpeg");
    }

    private List<ChapterGroup> rebuildGroups(Map<String, GroupDescriptor> descriptors) {
        List<ChapterGroup> groups = new ArrayList<>(descriptors.size());
        for (Map.Entry<String, GroupDescriptor> entry : descriptors.entrySet()) {
            GroupDescriptor descriptor = entry.getValue();
            ChapterGroup.Builder builder = ChapterGroup.builder()
                    .id(entry.getKey())
                    .title(descriptor.title())
                    .icon(descriptor.icon())
                    .visibility(descriptor.visibility())
                    .chapterIds(descriptor.chapterIds());
            groups.add(builder.build());
        }
        return groups;
    }

    private void replaceChapter(List<Chapter> chapters, Chapter replacement) {
        for (int i = 0; i < chapters.size(); i++) {
            if (chapters.get(i).id().equals(replacement.id())) {
                chapters.set(i, replacement);
                return;
            }
        }
        chapters.add(replacement);
    }

    private Chapter applyChapterResolution(ResolutionResult chapterResolution,
                                           ImportedChapter importedChapter,
                                           Map<String, Quest> existingChapterQuests,
                                           Set<String> usedQuestIds,
                                           ImportConflictPolicy questPolicy,
                                           List<String> warnings,
                                           List<String> addedQuests,
                                           List<String> renamedIds,
                                           Map<String, String> idRemap,
                                           List<String> mergedChapters,
                                           List<String> addedChapters) {
        Chapter.Builder builder = Chapter.builder()
                .id(chapterResolution.resolvedId)
                .title(importedChapter.title())
                .icon(new IconRef(importedChapter.icon()))
                .background(new BackgroundRef(importedChapter.background()))
                .visibility(importedChapter.visibility());

        if (chapterResolution.type == ResolutionType.MERGE) {
            mergedChapters.add(chapterResolution.resolvedId);
        } else {
            addedChapters.add(chapterResolution.resolvedId);
        }

        Map<String, Quest> questIndex = new LinkedHashMap<>(existingChapterQuests);
        Map<String, String> questRemap = new LinkedHashMap<>();
        for (Quest quest : existingChapterQuests.values()) {
            questRemap.put(quest.id(), quest.id());
        }

        List<Quest> quests = new ArrayList<>(existingChapterQuests.values());
        List<QuestPlan> plans = new ArrayList<>();

        for (ImportedQuest importedQuest : importedChapter.quests()) {
            String originalQuestId = normalizeId(importedQuest.id());
            boolean conflict = questIndex.containsKey(originalQuestId) || usedQuestIds.contains(originalQuestId);
            ResolutionResult questResolution = resolveId(originalQuestId, conflict, questPolicy, usedQuestIds, "quest", warnings, renamedIds, importedChapter.id());
            if (questResolution.type == ResolutionType.SKIP) {
                continue;
            }
            questRemap.put(importedQuest.id(), questResolution.resolvedId);
            idRemap.put(importedQuest.id(), questResolution.resolvedId);
            plans.add(new QuestPlan(importedQuest, questResolution));
        }

        for (QuestPlan plan : plans) {
            Quest quest = buildQuest(plan.quest(), plan.resolution().resolvedId(), questRemap, warnings);
            if (plan.resolution().type == ResolutionType.MERGE) {
                replaceQuest(quests, quest);
            } else {
                quests.add(quest);
                addedQuests.add(plan.resolution().resolvedId());
            }
            questIndex.put(quest.id(), quest);
        }

        for (Quest quest : quests) {
            builder.addQuest(quest);
        }
        for (ChapterImage image : convertImages(importedChapter.images())) {
            builder.addImage(image);
        }
        for (QuestLink link : convertQuestLinks(importedChapter.questLinks(), questRemap)) {
            builder.addQuestLink(link);
        }
        return builder.build();
    }

    private List<QuestLink> convertQuestLinks(List<Map<String, Object>> rawLinks, Map<String, String> questRemap) {
        if (rawLinks == null || rawLinks.isEmpty()) {
            return List.of();
        }
        List<QuestLink> result = new ArrayList<>();
        for (Map<String, Object> props : rawLinks) {
            String id = Optional.ofNullable(props.get("id")).map(Object::toString).orElseGet(HexId::generate);
            String linkedQuestId = Optional.ofNullable(props.get("linked_quest")).map(Object::toString).orElse(null);
            if (linkedQuestId == null) {
                continue;
            }
            linkedQuestId = questRemap.getOrDefault(linkedQuestId, linkedQuestId);
            double x = Optional.ofNullable(props.get("x")).map(this::toDouble).orElse(0.0);
            double y = Optional.ofNullable(props.get("y")).map(this::toDouble).orElse(0.0);
            result.add(new QuestLink(id, linkedQuestId, x, y));
        }
        return result;
    }

    private List<ChapterImage> convertImages(List<Map<String, Object>> rawImages) {
        if (rawImages == null || rawImages.isEmpty()) {
            return List.of();
        }
        List<ChapterImage> result = new ArrayList<>();
        for (Map<String, Object> props : rawImages) {
            String image = Optional.ofNullable(props.get("image")).map(Object::toString).orElse("minecraft:textures/misc/unknown_pack.png");
            double x = Optional.ofNullable(props.get("x")).map(this::toDouble).orElse(0.0);
            double y = Optional.ofNullable(props.get("y")).map(this::toDouble).orElse(0.0);
            double width = Optional.ofNullable(props.get("width")).map(this::toDouble).orElse(1.0);
            double height = Optional.ofNullable(props.get("height")).map(this::toDouble).orElse(1.0);
            double rotation = Optional.ofNullable(props.get("rotation")).map(this::toDouble).orElse(0.0);
            Integer color = Optional.ofNullable(props.get("color")).map(this::toInt).orElse(null);
            Integer alpha = Optional.ofNullable(props.get("alpha")).map(this::toInt).orElse(null);
            List<String> hover = extractHoverText(props.get("hover"));
            result.add(new ChapterImage(image, x, y, width, height, rotation, color, alpha, hover));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractHoverText(Object raw) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                result.add(item.toString());
            }
            return result;
        }
        if (raw instanceof String string) {
            return List.of(string);
        }
        return List.of();
    }

    private void replaceQuest(List<Quest> quests, Quest replacement) {
        for (int i = 0; i < quests.size(); i++) {
            if (quests.get(i).id().equals(replacement.id())) {
                quests.set(i, replacement);
                return;
            }
        }
        quests.add(replacement);
    }

    private Quest buildQuest(ImportedQuest importedQuest,
                             String questId,
                             Map<String, String> questIdRemap,
                             List<String> warnings) {
        Quest.Builder builder = Quest.builder()
                .id(questId)
                .title(importedQuest.title())
                .description(Optional.ofNullable(importedQuest.description()).orElse(""))
                .icon(new IconRef(importedQuest.icon()))
                .visibility(importedQuest.visibility());

        Map<String, Object> props = importedQuest.properties();
        Optional.ofNullable(props.get("shape"))
                .map(Object::toString)
                .map(QuestShape::fromString)
                .ifPresent(builder::shape);
        Optional.ofNullable(props.get("size"))
                .map(this::toDouble)
                .ifPresent(builder::size);
        Optional.ofNullable(props.get("x"))
                .map(this::toDouble)
                .ifPresent(builder::x);
        Optional.ofNullable(props.get("y"))
                .map(this::toDouble)
                .ifPresent(builder::y);

        for (ImportedTask task : importedQuest.tasks()) {
            convertTask(task).ifPresent(builder::addTask);
        }

        for (ImportedReward reward : importedQuest.rewards()) {
            convertReward(reward).ifPresent(builder::addReward);
        }

        for (ImportedDependency dependency : importedQuest.dependencies()) {
            String remapped = questIdRemap.getOrDefault(dependency.questId(), dependency.questId());
            builder.addDependency(new Dependency(remapped, dependency.required()));
        }

        Quest quest = builder.build();
        if (quest.tasks().isEmpty()) {
            warnings.add("Quest " + questId + " has no tasks after conversion.");
        }
        return quest;
    }

    private Optional<Task> convertTask(ImportedTask task) {
        Map<String, Object> props = task.properties();
        return switch (task.type().toLowerCase(Locale.ROOT)) {
            case "item" -> convertItemTask(props);
            case "advancement" -> convertAdvancementTask(props);
            case "location" -> convertLocationTask(props);
            case "checkmark" -> Optional.of(new CheckmarkTask());
            case "kill" -> convertKillTask(props);
            case "observation" -> convertObservationTask(props);
            case "gamestage", "stage" -> convertStageTask(props);
            case "dimension" -> convertDimensionTask(props);
            case "biome" -> convertBiomeTask(props);
            case "structure" -> convertStructureTask(props);
            case "xp" -> convertXpTask(props);
            case "stat" -> convertStatTask(props);
            case "fluid" -> convertFluidTask(props);
            case "custom" -> convertCustomTask(props);
            default -> Optional.empty();
        };
    }

    private Optional<Task> convertItemTask(Map<String, Object> props) {
        Object itemValue = props.get("item");
        if (itemValue instanceof Map<?, ?> itemMapRaw) {
            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMapRaw.forEach((k, v) -> itemMap.put(String.valueOf(k), v));
            String itemId = Optional.ofNullable(itemMap.get("id")).map(Object::toString).orElse(null);
            int count = Optional.ofNullable(itemMap.get("count")).map(this::toInt).orElse(1);
            boolean consume = Optional.ofNullable(props.get("consume")).map(this::toBoolean).orElse(true);
            if (itemId != null) {
                return Optional.of(new ItemTask(new ItemRef(itemId, count), consume));
            }
        }
        return Optional.empty();
    }

    private Optional<Task> convertAdvancementTask(Map<String, Object> props) {
        String advancement = Optional.ofNullable(props.get("advancement"))
                .or(() -> Optional.ofNullable(props.get("advancement_id")))
                .map(Object::toString)
                .orElse(null);
        if (advancement != null) {
            return Optional.of(new AdvancementTask(advancement));
        }
        return Optional.empty();
    }

    private Optional<Task> convertLocationTask(Map<String, Object> props) {
        String dimension = Optional.ofNullable(props.get("dimension")).map(Object::toString).orElse("minecraft:overworld");
        double x = Optional.ofNullable(props.get("x")).map(this::toDouble).orElse(0.0);
        double y = Optional.ofNullable(props.get("y")).map(this::toDouble).orElse(0.0);
        double z = Optional.ofNullable(props.get("z")).map(this::toDouble).orElse(0.0);
        double radius = Optional.ofNullable(props.get("radius")).map(this::toDouble).orElse(5.0);
        return Optional.of(new LocationTask(dimension, x, y, z, radius));
    }

    private Optional<Task> convertKillTask(Map<String, Object> props) {
        String entity = Optional.ofNullable(props.get("entity"))
                .or(() -> Optional.ofNullable(props.get("mob")))
                .map(Object::toString)
                .orElse("minecraft:zombie");
        long count = Optional.ofNullable(props.get("value"))
                .or(() -> Optional.ofNullable(props.get("count")))
                .map(this::toLong)
                .orElse(1L);
        String entityTag = Optional.ofNullable(props.get("entity_tag")).map(Object::toString).orElse(null);
        String customName = Optional.ofNullable(props.get("custom_name")).map(Object::toString).orElse(null);
        return Optional.of(new KillTask(entity, count, entityTag, customName));
    }

    private Optional<Task> convertObservationTask(Map<String, Object> props) {
        String observeType = Optional.ofNullable(props.get("observe_type"))
                .or(() -> Optional.ofNullable(props.get("timer_type")))
                .map(Object::toString)
                .orElse("BLOCK");
        String toObserve = Optional.ofNullable(props.get("to_observe"))
                .or(() -> Optional.ofNullable(props.get("block")))
                .map(Object::toString)
                .orElse("minecraft:stone");
        long timer = Optional.ofNullable(props.get("timer")).map(this::toLong).orElse(0L);
        try {
            ObservationTask.ObserveType type = ObservationTask.ObserveType.valueOf(observeType.toUpperCase(Locale.ROOT));
            return Optional.of(new ObservationTask(type, toObserve, timer));
        } catch (IllegalArgumentException e) {
            return Optional.of(new ObservationTask(ObservationTask.ObserveType.BLOCK, toObserve, timer));
        }
    }

    private Optional<Task> convertStageTask(Map<String, Object> props) {
        String stage = Optional.ofNullable(props.get("stage")).map(Object::toString).orElse("default_stage");
        boolean teamStage = Optional.ofNullable(props.get("team_stage")).map(this::toBoolean).orElse(false);
        return Optional.of(new StageTask(stage, teamStage));
    }

    private Optional<Task> convertDimensionTask(Map<String, Object> props) {
        String dimension = Optional.ofNullable(props.get("dimension")).map(Object::toString).orElse("minecraft:overworld");
        return Optional.of(new DimensionTask(dimension));
    }

    private Optional<Task> convertBiomeTask(Map<String, Object> props) {
        String biome = Optional.ofNullable(props.get("biome")).map(Object::toString).orElse("minecraft:plains");
        return Optional.of(new BiomeTask(biome));
    }

    private Optional<Task> convertStructureTask(Map<String, Object> props) {
        String structure = Optional.ofNullable(props.get("structure")).map(Object::toString).orElse("minecraft:village");
        return Optional.of(new StructureTask(structure));
    }

    private Optional<Task> convertXpTask(Map<String, Object> props) {
        long value = Optional.ofNullable(props.get("value"))
                .or(() -> Optional.ofNullable(props.get("xp")))
                .map(this::toLong)
                .orElse(1L);
        boolean points = Optional.ofNullable(props.get("points")).map(this::toBoolean).orElse(false);
        return Optional.of(new XpTask(value, points));
    }

    private Optional<Task> convertStatTask(Map<String, Object> props) {
        String stat = Optional.ofNullable(props.get("stat")).map(Object::toString).orElse("minecraft:walk_one_cm");
        int value = Optional.ofNullable(props.get("value")).map(this::toInt).orElse(1);
        return Optional.of(new StatTask(stat, value));
    }

    private Optional<Task> convertFluidTask(Map<String, Object> props) {
        String fluid = Optional.ofNullable(props.get("fluid")).map(Object::toString).orElse("minecraft:water");
        long amount = Optional.ofNullable(props.get("amount")).map(this::toLong).orElse(1000L);
        return Optional.of(new FluidTask(fluid, amount));
    }

    private Optional<Task> convertCustomTask(Map<String, Object> props) {
        long maxProgress = Optional.ofNullable(props.get("max_progress"))
                .or(() -> Optional.ofNullable(props.get("value")))
                .map(this::toLong)
                .orElse(1L);
        return Optional.of(new CustomTask(maxProgress));
    }

    private Optional<Reward> convertReward(ImportedReward reward) {
        Map<String, Object> props = reward.properties();
        return switch (reward.type().toLowerCase(Locale.ROOT)) {
            case "item" -> convertItemReward(props);
            case "loot_table" -> Optional.ofNullable(props.get("table")).map(Object::toString).map(Reward::lootTable);
            case "xp_levels", "xp_level" -> Optional.ofNullable(props.get("xp_levels"))
                    .or(() -> Optional.ofNullable(props.get("amount")))
                    .map(this::toInt).map(Reward::xpLevels);
            case "xp", "xp_amount" -> Optional.ofNullable(props.get("xp"))
                    .or(() -> Optional.ofNullable(props.get("amount")))
                    .map(this::toInt).map(Reward::xpAmount);
            case "command" -> convertCommandReward(props);
            default -> Optional.empty();
        };
    }

    private Optional<Reward> convertItemReward(Map<String, Object> props) {
        Object itemValue = props.get("item");
        if (itemValue instanceof Map<?, ?> itemMapRaw) {
            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMapRaw.forEach((k, v) -> itemMap.put(String.valueOf(k), v));
            String itemId = Optional.ofNullable(itemMap.get("id")).map(Object::toString).orElse(null);
            int count = Optional.ofNullable(itemMap.get("count")).map(this::toInt).orElse(1);
            if (itemId != null) {
                return Optional.of(Reward.item(new ItemRef(itemId, count)));
            }
        }
        return Optional.empty();
    }

    private Optional<Reward> convertCommandReward(Map<String, Object> props) {
        String command = Optional.ofNullable(props.get("command")).map(Object::toString).orElse(null);
        boolean runAsServer = Optional.ofNullable(props.get("run_as_server")).map(this::toBoolean).orElse(true);
        if (command != null) {
            return Optional.of(Reward.command(new RewardCommand(command, runAsServer)));
        }
        return Optional.empty();
    }

    private String resolveGroupForChapter(ImportedChapter chapter,
                                          ImportOptions options,
                                          Map<String, String> groupIdRemap,
                                          Map<String, GroupDescriptor> groupDescriptors,
                                          String fallbackGroupId,
                                          List<String> warnings) {
        if (options.targetGroupId() != null && groupDescriptors.containsKey(options.targetGroupId())) {
            return options.targetGroupId();
        }
        if (chapter.groupId() != null) {
            String mapped = groupIdRemap.getOrDefault(chapter.groupId(), chapter.groupId());
            if (groupDescriptors.containsKey(mapped)) {
                return mapped;
            }
            warnings.add("Chapter " + chapter.id() + " references missing group " + chapter.groupId() + ". Using fallback group.");
        }
        return fallbackGroupId;
    }

    private String determineFallbackGroup(Map<String, GroupDescriptor> descriptors, List<String> warnings) {
        if (!descriptors.isEmpty()) {
            return descriptors.keySet().iterator().next();
        }
        // Create a default group if none exist
        String defaultId = "group_import";
        descriptors.put(defaultId, new GroupDescriptor("Imported", new IconRef("minecraft:book"), Visibility.VISIBLE, new ArrayList<>()));
        warnings.add("Created default chapter group '" + defaultId + "' because none existed.");
        return defaultId;
    }

    private ResolutionResult resolveId(String originalId,
                                       boolean conflict,
                                       ImportConflictPolicy policy,
                                       Set<String> usedIds,
                                       String label,
                                       List<String> warnings,
                                       List<String> renamedIds,
                                       String packId) {
        String normalized = normalizeId(originalId);
        if (!conflict && !usedIds.contains(normalized)) {
            usedIds.add(normalized);
            return new ResolutionResult(normalized, ResolutionType.NEW);
        }
        if (!conflict) {
            usedIds.add(normalized);
            return new ResolutionResult(normalized, ResolutionType.NEW);
        }
        return switch (policy) {
            case SKIP -> {
                warnings.add("Skipped " + label + " " + originalId + " because of an ID conflict.");
                yield new ResolutionResult(originalId, ResolutionType.SKIP);
            }
            case MERGE_BY_ID -> new ResolutionResult(originalId, ResolutionType.MERGE);
            case RENAME -> {
                String candidate = normalized + "_import";
                int index = 1;
                while (usedIds.contains(candidate)) {
                    candidate = normalized + "_import" + index++;
                }
                usedIds.add(candidate);
                renamedIds.add(originalId + " -> " + candidate);
                yield new ResolutionResult(candidate, ResolutionType.NEW);
            }
            case NEW_IDS -> {
                String candidate = generateDeterministicId(packId + ":" + normalized);
                while (usedIds.contains(candidate)) {
                    candidate = generateDeterministicId(candidate + HexId.generate());
                }
                usedIds.add(candidate);
                renamedIds.add(originalId + " -> " + candidate);
                yield new ResolutionResult(candidate, ResolutionType.NEW);
            }
        };
    }

    private String normalizeId(String id) {
        if (id == null) {
            return HexId.generate();
        }
        return id.trim();
    }

    private String generateDeterministicId(Object seed) {
        return HexId.fromSeed(seed.toString());
    }

    private int toInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(value.toString());
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString());
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return Double.parseDouble(value.toString());
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private record GroupDescriptor(String title,
                                   IconRef icon,
                                   Visibility visibility,
                                   List<String> chapterIds) {
    }

    private enum ResolutionType {
        NEW,
        MERGE,
        SKIP
    }

    private record ResolutionResult(String resolvedId, ResolutionType type) {
    }

    private record QuestPlan(ImportedQuest quest, ResolutionResult resolution) {
    }
}
