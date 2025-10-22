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
    private static final int PAGE_LIMIT = 512;
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

        List<StoreDao.ItemEntity> entities = storeDao.listItems(
                filter,
                tagFilters,
                modFilter,
                null,
                kindFilter,
                selectedSortMode,
                PAGE_LIMIT,
                0);

        if (vanillaOnly.get()) {
            entities = entities.stream()
                    .filter(StoreDao.ItemEntity::isVanilla)
                    .toList();
        }

        List<ItemRow> rows = entities.stream()
                .map(ItemRow::new)
                .collect(Collectors.toCollection(ArrayList::new));
        items.setAll(rows);
    }

    public void loadFilterOptions() {
        int filterPageSize = Math.max(PAGE_LIMIT, 2048);
        List<StoreDao.ItemEntity> entities = storeDao.listItems(
                null,
                List.of(),
                null,
                null,
                null,
                StoreDao.SortMode.NAME,
                filterPageSize,
                0);

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
            if ((modId == null || modId.isBlank()) && entity.isVanilla()) {
                modId = "minecraft";
            }
            String label = modName;
            if (label == null || label.isBlank()) {
                if (modId != null && !modId.isBlank()) {
                    label = prettifyModId(modId);
                } else if (entity.isVanilla()) {
                    label = "Minecraft";
                }
            }
            if (modId != null && !modId.isBlank()) {
                mods.putIfAbsent(modId.toLowerCase(Locale.ROOT), new ModOption(modId, label == null ? modId : label));
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
                return entity.modName();
            }
            if (entity.modId() != null && !entity.modId().isBlank()) {
                return entity.modId();
            }
            return entity.isVanilla() ? "Minecraft" : "Unknown";
        }

        public boolean isVanilla() {
            return entity.isVanilla();
        }

        public String iconHash() {
            return entity.iconHash();
        }

        public String kind() {
            return entity.kind();
        }
    }

    public record ModOption(String value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }
}
