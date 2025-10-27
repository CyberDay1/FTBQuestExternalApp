package dev.ftbq.editor.viewmodel;

import dev.ftbq.editor.store.StoreDao;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ItemBrowserViewModel {
    private static final int PAGE_SIZE = 1024;
    private static final String MINECRAFT_MOD_ID = "minecraft";
    private static final Pattern TAG_PATTERN = Pattern.compile("\\\"([^\\\\\\\"]+)\\\"");

    private final StoreDao storeDao;

    private final ObservableList<ItemRow> items = FXCollections.observableArrayList();
    private final ObservableList<ModOption> availableMods = FXCollections.observableArrayList();
    private final ObservableList<String> availableTags = FXCollections.observableArrayList();
    private final ObservableList<String> availableKinds = FXCollections.observableArrayList();

    private final StringProperty searchText = new SimpleStringProperty("");
    private final ObjectProperty<ModOption> selectedMod = new SimpleObjectProperty<>(null);
    private final ObjectProperty<String> selectedTag = new SimpleObjectProperty<>(null);
    private final ObjectProperty<String> selectedKind = new SimpleObjectProperty<>(null);
    private final BooleanProperty vanillaOnly = new SimpleBooleanProperty(false);
    private final ObjectProperty<StoreDao.SortMode> sortMode = new SimpleObjectProperty<>(StoreDao.SortMode.NAME);

    public ItemBrowserViewModel(StoreDao storeDao) {
        this.storeDao = Objects.requireNonNull(storeDao, "storeDao");
    }

    public ObservableList<ItemRow> getItems() {
        return items;
    }

    public ObservableList<ModOption> getAvailableMods() {
        return availableMods;
    }

    public ObservableList<String> getAvailableTags() {
        return availableTags;
    }

    public ObservableList<String> getAvailableKinds() {
        return availableKinds;
    }

    public StringProperty searchTextProperty() {
        return searchText;
    }

    public ObjectProperty<ModOption> selectedModProperty() {
        return selectedMod;
    }

    public ObjectProperty<String> selectedTagProperty() {
        return selectedTag;
    }

    public ObjectProperty<String> selectedKindProperty() {
        return selectedKind;
    }

    public BooleanProperty vanillaOnlyProperty() {
        return vanillaOnly;
    }

    public ObjectProperty<StoreDao.SortMode> sortModeProperty() {
        return sortMode;
    }

    public void refresh() {
        String filter = normalise(searchText.get());
        List<String> tagFilters = Optional.ofNullable(selectedTag.get())
                .filter(value -> !value.isBlank())
                .map(List::of)
                .orElse(List.of());
        String modFilter = Optional.ofNullable(selectedMod.get())
                .map(ModOption::value)
                .filter(value -> !value.isBlank())
                .orElse(null);
        String kindFilter = Optional.ofNullable(selectedKind.get())
                .filter(value -> !value.isBlank())
                .orElse(null);
        StoreDao.SortMode selectedSortMode = Optional.ofNullable(sortMode.get()).orElse(StoreDao.SortMode.NAME);

        List<StoreDao.ItemEntity> entities = fetchAllItems(
                filter,
                tagFilters,
                modFilter,
                null,
                kindFilter,
                selectedSortMode);

        if (vanillaOnly.get()) {
            entities = entities.stream()
                    .filter(ItemBrowserViewModel::isVanillaEntity)
                    .toList();
        }

        List<ItemRow> rows = entities.stream()
                .map(ItemRow::new)
                .collect(Collectors.toCollection(ArrayList::new));
        items.setAll(rows);
    }

    public void loadFilterOptions() {
        List<StoreDao.ItemEntity> entities = fetchAllItems(
                null,
                List.of(),
                null,
                null,
                null,
                StoreDao.SortMode.NAME);

        Map<String, ModOption> mods = new LinkedHashMap<>();
        Set<String> tags = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> kinds = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        for (StoreDao.ItemEntity entity : entities) {
            if (entity.kind() != null && !entity.kind().isBlank()) {
                kinds.add(entity.kind());
            }

            for (String tag : parseTags(entity.tags())) {
                tags.add(tag);
            }

            String modId = entity.modId();
            String modName = entity.modName();
            boolean vanillaEntity = isVanillaEntity(entity);
            if ((modId == null || modId.isBlank()) && vanillaEntity) {
                modId = MINECRAFT_MOD_ID;
            }

            boolean isMinecraftMod = isMinecraftModId(modId);
            boolean isVanilla = vanillaEntity || isMinecraftMod;

            String label = modName;
            if (label == null || label.isBlank()) {
                if (isVanilla) {
                    label = "Minecraft";
                } else if (modId != null && !modId.isBlank()) {
                    label = prettifyModId(modId);
                }
            }
            if (label == null || label.isBlank()) {
                if (modId != null && !modId.isBlank()) {
                    label = modId;
                } else {
                    label = "Minecraft";
                }
            }

            if (modId != null && !modId.isBlank()) {
                String key = modId.toLowerCase(Locale.ROOT);
                ModOption existing = mods.get(key);
                ModOption candidate = new ModOption(modId, label, isVanilla);
                if (existing == null || (!existing.vanilla() && candidate.vanilla())) {
                    mods.put(key, candidate);
                }
            }
        }

        availableMods.setAll(mods.values().stream()
                .sorted(Comparator.comparing(ModOption::label, String.CASE_INSENSITIVE_ORDER))
                .toList());
        availableTags.setAll(tags);
        availableKinds.setAll(kinds);
    }

    private List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        Matcher matcher = TAG_PATTERN.matcher(tagsJson);
        while (matcher.find()) {
            String tag = matcher.group(1);
            if (!tag.isBlank()) {
                result.add(tag);
            }
        }
        return result;
    }

    private String normalise(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        return input.trim();
    }

    private List<StoreDao.ItemEntity> fetchAllItems(
            String filter,
            List<String> tagFilters,
            String modFilter,
            String version,
            String kindFilter,
            StoreDao.SortMode sortMode) {
        List<StoreDao.ItemEntity> allItems = new ArrayList<>();
        int offset = 0;
        while (true) {
            List<StoreDao.ItemEntity> page = storeDao.listItems(
                    filter,
                    tagFilters,
                    modFilter,
                    version,
                    kindFilter,
                    sortMode,
                    PAGE_SIZE,
                    offset);
            if (page.isEmpty()) {
                break;
            }
            allItems.addAll(page);
            if (page.size() < PAGE_SIZE) {
                break;
            }
            offset += page.size();
        }
        return allItems;
    }

    private static boolean isMinecraftModId(String modId) {
        return modId != null && modId.equalsIgnoreCase(MINECRAFT_MOD_ID);
    }

    private static boolean isVanillaEntity(StoreDao.ItemEntity entity) {
        return entity != null && (entity.isVanilla() || isMinecraftModId(entity.modId()));
    }

    private String prettifyModId(String modId) {
        String[] parts = modId.split("[_-]");
        return java.util.Arrays.stream(parts)
                .filter(part -> !part.isBlank())
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }

    public record ItemRow(StoreDao.ItemEntity entity) {
        public String id() {
            return entity.id();
        }

        public String displayName() {
            return entity.displayName();
        }

        public String modDisplay() {
            if (entity.modName() != null && !entity.modName().isBlank()) {
                if (isMinecraftModId(entity.modId())) {
                    return "Minecraft";
                }
                return entity.modName();
            }
            if (entity.modId() != null && !entity.modId().isBlank()) {
                if (isMinecraftModId(entity.modId())) {
                    return "Minecraft";
                }
                return entity.modId();
            }
            return isVanillaEntity(entity) ? "Minecraft" : "Unknown";
        }

        public boolean isVanilla() {
            return isVanillaEntity(entity);
        }

        public String iconHash() {
            return entity.iconHash();
        }

        public String kind() {
            return entity.kind();
        }
    }

    public record ModOption(String value, String label, boolean vanilla) {
        @Override
        public String toString() {
            return label;
        }
    }
}
