package dev.ftbq.editor.view;

import dev.ftbq.editor.assets.CacheManager;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.ItemReward;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.RewardCommand;
import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.domain.version.ItemCatalog;
import dev.ftbq.editor.domain.version.VersionCatalog;
import dev.ftbq.editor.io.snbt.SnbtLootTableParser;
import dev.ftbq.editor.services.UiServiceLocator;
import dev.ftbq.editor.store.StoreDao;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.StringConverter;

import java.io.ByteArrayInputStream;
import java.util.*;

/**
 * Presents a dialog for editing quest details, including expanded reward configuration.
 */
public final class QuestEditDialogController {

    private static final int MAX_ITEM_REWARDS = 5;
    private static final int MAX_ITEM_COUNT = 999;
    private static final SnbtLootTableParser LOOT_TABLE_PARSER = new SnbtLootTableParser();
    private static final int DEFAULT_LOOT_WEIGHT = 100;
    private static final String SNBT_INDENT = "  ";

    private final Map<String, EditableLootTable> lootTables = new LinkedHashMap<>();
    private final Map<String, Optional<Image>> itemIconCache = new HashMap<>();
    private EditableLootTable activeLootTable;
    private boolean updatingLootTableIcon;

    public Optional<Quest> editQuest(Quest quest) {
        Objects.requireNonNull(quest, "quest");
        Dialog<Quest> dialog = new Dialog<>();
        dialog.setTitle("Edit Quest");
        dialog.setHeaderText("Update quest details");

        ButtonType saveButtonType = new ButtonType("Save", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField titleField = new TextField(quest.title());
        TextArea descriptionArea = new TextArea(quest.description());
        descriptionArea.setPrefRowCount(5);
        TextField iconField = new TextField(quest.icon() != null ? quest.icon().icon() : "minecraft:book");
        ComboBox<Visibility> visibilityBox = new ComboBox<>(FXCollections.observableArrayList(Visibility.values()));
        visibilityBox.setValue(quest.visibility());
        Label idValue = new Label(quest.id());

        lootTables.clear();
        lootTables.putAll(loadLootTables());
        ObservableList<String> lootTableNames = FXCollections.observableArrayList(lootTables.keySet());
        lootTableNames.add(0, "");
        ComboBox<String> lootTableBox = createLootTableComboBox(lootTableNames);
        String questLootTable = quest.lootTableId();
        if (questLootTable != null && !questLootTable.isBlank() && !lootTableNames.contains(questLootTable)) {
            lootTableNames.add(questLootTable);
        }

        ObservableList<ItemOption> itemOptions = FXCollections.observableArrayList();
        Map<String, ItemOption> optionIndex = new LinkedHashMap<>();
        Comparator<ItemOption> optionComparator = Comparator.comparing(ItemOption::displayLabel, String.CASE_INSENSITIVE_ORDER);
        populateBaseItemOptions(quest, optionIndex, itemOptions, optionComparator, lootTables);

        StringConverter<ItemOption> itemConverter = createItemConverter(optionIndex, itemOptions, optionComparator);
        List<ItemRewardRow> itemRows = createItemRows(itemOptions, itemConverter);
        applyExistingItemRewards(quest, optionIndex, itemOptions, optionComparator, itemRows);

        ComboBox<EditableLootItem> lootItemBox = createLootItemComboBox();
        Button addLootItemButton = new Button("Add Item to Rewards");
        addLootItemButton.setDisable(true);
        lootItemBox.setDisable(true);

        ComboBox<ItemOption> lootTableIconBox = new ComboBox<>(itemOptions);
        lootTableIconBox.setEditable(true);
        lootTableIconBox.setConverter(itemConverter);

        ImageView lootTableIconPreview = new ImageView();
        lootTableIconPreview.setFitWidth(32);
        lootTableIconPreview.setFitHeight(32);
        lootTableIconPreview.setPreserveRatio(true);
        HBox lootTableIconControls = new HBox(8, lootTableIconBox, lootTableIconPreview);

        VBox lootTableEntriesBox = new VBox(6);
        lootTableEntriesBox.setFillWidth(true);

        lootTableBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            saveActiveLootTableState(lootTableIconBox, optionIndex, itemOptions, optionComparator, lootTableIconPreview);
            EditableLootTable table = lootTables.get(newValue);
            activeLootTable = table;
            List<EditableLootItem> items = table == null ? List.of() : table.items();
            lootItemBox.setItems(FXCollections.observableArrayList(items));
            boolean hasItems = table != null && !table.items().isEmpty();
            lootItemBox.setDisable(!hasItems);
            addLootItemButton.setDisable(!hasItems);
            if (hasItems) lootItemBox.getSelectionModel().selectFirst();
            updateLootTableIconControls(table, lootTableIconBox, lootTableIconPreview, optionIndex, itemOptions, optionComparator);
            populateLootTableEntriesBox(table, lootTableEntriesBox);
        });

        lootTableIconBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (updatingLootTableIcon) return;
            ItemOption selection = newValue;
            if (selection == null) {
                String text = lootTableIconBox.getEditor().getText();
                if (text != null && !text.isBlank()) {
                    selection = ensureItemOption(text.trim(), null, optionIndex, itemOptions, optionComparator);
                    if (selection != null) {
                        updatingLootTableIcon = true;
                        try {
                            lootTableIconBox.setValue(selection);
                        } finally {
                            updatingLootTableIcon = false;
                        }
                    }
                }
            }
            String iconId = selection == null ? "" : selection.itemId();
            updateActiveLootTableIcon(iconId, lootTableIconPreview);
        });

        String initialLootTable = questLootTable != null ? questLootTable : "";
        lootTableBox.setValue(initialLootTable);
        EditableLootTable initialTable = lootTables.get(initialLootTable);
        if (initialTable != null) {
            activeLootTable = initialTable;
            lootItemBox.setItems(FXCollections.observableArrayList(initialTable.items()));
            if (!initialTable.items().isEmpty()) {
                lootItemBox.getSelectionModel().selectFirst();
                lootItemBox.setDisable(false);
                addLootItemButton.setDisable(false);
            }
            updateLootTableIconControls(initialTable, lootTableIconBox, lootTableIconPreview, optionIndex, itemOptions, optionComparator);
            populateLootTableEntriesBox(initialTable, lootTableEntriesBox);
        }

        addLootItemButton.setOnAction(event -> {
            EditableLootItem selected = lootItemBox.getValue();
            if (selected == null) return;
            ItemOption option = ensureItemOption(selected.itemId(), selected.displayName(), optionIndex, itemOptions, optionComparator);
            ItemRewardRow targetRow = itemRows.stream().filter(ItemRewardRow::isEmpty).findFirst().orElse(itemRows.get(0));
            targetRow.setItem(option, selected.defaultCount());
        });

        VBox itemRowsContainer = new VBox(6);
        itemRows.stream().map(ItemRewardRow::node).forEach(itemRowsContainer.getChildren()::add);
        HBox lootItemControls = new HBox(8, lootItemBox, addLootItemButton);
        VBox itemSection = new VBox(8, itemRowsContainer, lootItemControls);
        VBox.setVgrow(itemRowsContainer, Priority.NEVER);

        ToggleGroup xpToggle = new ToggleGroup();
        RadioButton noXpButton = new RadioButton("No XP reward");
        RadioButton xpAmountButton = new RadioButton("XP amount");
        RadioButton xpLevelsButton = new RadioButton("XP levels");
        noXpButton.setToggleGroup(xpToggle);
        xpAmountButton.setToggleGroup(xpToggle);
        xpLevelsButton.setToggleGroup(xpToggle);

        Spinner<Integer> xpAmountSpinner = new Spinner<>(new IntegerSpinnerValueFactory(1, Integer.MAX_VALUE, 1));
        xpAmountSpinner.setEditable(true);
        Spinner<Integer> xpLevelSpinner = new Spinner<>(new IntegerSpinnerValueFactory(1, Integer.MAX_VALUE, 1));
        xpLevelSpinner.setEditable(true);
        xpAmountSpinner.disableProperty().bind(xpToggle.selectedToggleProperty().isNotEqualTo(xpAmountButton));
        xpLevelSpinner.disableProperty().bind(xpToggle.selectedToggleProperty().isNotEqualTo(xpLevelsButton));

        if (quest.experienceAmount() != null) {
            xpToggle.selectToggle(xpAmountButton);
            xpAmountSpinner.getValueFactory().setValue(Math.max(1, quest.experienceAmount()));
        } else if (quest.experienceLevels() != null) {
            xpToggle.selectToggle(xpLevelsButton);
            xpLevelSpinner.getValueFactory().setValue(Math.max(1, quest.experienceLevels()));
        } else {
            xpToggle.selectToggle(noXpButton);
        }

        VBox xpSection = new VBox(6,
                new HBox(8, xpAmountButton, xpAmountSpinner),
                new HBox(8, xpLevelsButton, xpLevelSpinner),
                noXpButton
        );

        TextField commandField = new TextField();
        if (quest.commandReward() != null) commandField.setText(quest.commandReward().command());

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        grid.add(new Label("ID"), 0, 0);
        grid.add(idValue, 1, 0);
        grid.add(new Label("Title"), 0, 1);
        grid.add(titleField, 1, 1);
        grid.add(new Label("Description"), 0, 2);
        grid.add(descriptionArea, 1, 2);
        grid.add(new Label("Icon"), 0, 3);
        grid.add(iconField, 1, 3);
        grid.add(new Label("Visibility"), 0, 4);
        grid.add(visibilityBox, 1, 4);
        grid.add(new Label("Loot table"), 0, 5);
        grid.add(lootTableBox, 1, 5);
        grid.add(new Label("Loot table icon"), 0, 6);
        grid.add(lootTableIconControls, 1, 6);
        grid.add(new Label("Loot table entries"), 0, 7);
        grid.add(lootTableEntriesBox, 1, 7);
        grid.add(new Label("Loot items"), 0, 8);
        grid.add(lootItemControls, 1, 8);
        grid.add(new Label("Item rewards"), 0, 9);
        grid.add(itemSection, 1, 9);
        grid.add(new Label("XP reward"), 0, 10);
        grid.add(xpSection, 1, 10);
        grid.add(new Label("Command reward"), 0, 11);
        grid.add(commandField, 1, 11);

        GridPane.setHgrow(titleField, Priority.ALWAYS);
        GridPane.setHgrow(descriptionArea, Priority.ALWAYS);
        GridPane.setHgrow(iconField, Priority.ALWAYS);
        GridPane.setHgrow(lootTableBox, Priority.ALWAYS);
        GridPane.setHgrow(lootTableIconControls, Priority.ALWAYS);
        GridPane.setHgrow(lootTableEntriesBox, Priority.ALWAYS);
        GridPane.setHgrow(itemSection, Priority.ALWAYS);
        GridPane.setHgrow(commandField, Priority.ALWAYS);

        dialog.getDialogPane().setContent(grid);

        Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.setDisable(titleField.getText() == null || titleField.getText().isBlank());
        titleField.textProperty().addListener((obs, oldValue, newValue) ->
                saveButton.setDisable(newValue == null || newValue.isBlank()));

        dialog.setResultConverter(button -> {
            if (button != saveButtonType) return null;
            saveActiveLootTableState(lootTableIconBox, optionIndex, itemOptions, optionComparator, lootTableIconPreview);
            persistLootTableChanges();
            String title = Optional.ofNullable(titleField.getText()).orElse("").trim();
            String description = Optional.ofNullable(descriptionArea.getText()).orElse("").trim();
            String iconText = Optional.ofNullable(iconField.getText()).orElse("").trim();
            Visibility visibility = Optional.ofNullable(visibilityBox.getValue()).orElse(Visibility.VISIBLE);

            List<ItemReward> rewards = new ArrayList<>();
            for (ItemRewardRow row : itemRows) row.toReward().ifPresent(rewards::add);

            Integer xpAmount = null;
            Integer xpLevels = null;
            if (xpToggle.getSelectedToggle() == xpAmountButton) xpAmount = readSpinnerValue(xpAmountSpinner);
            else if (xpToggle.getSelectedToggle() == xpLevelsButton) xpLevels = readSpinnerValue(xpLevelSpinner);

            String selectedLootTable = lootTableBox.getValue();
            if (selectedLootTable != null && selectedLootTable.isBlank()) selectedLootTable = null;

            RewardCommand command = null;
            String commandText = commandField.getText();
            if (commandText != null && !commandText.isBlank()) command = new RewardCommand(commandText.trim(), false);

            Quest.Builder builder = Quest.builder()
                    .id(quest.id())
                    .title(title)
                    .description(description)
                    .icon(new IconRef(iconText.isEmpty() ? "minecraft:book" : iconText))
                    .visibility(visibility)
                    .tasks(quest.tasks())
                    .dependencies(quest.dependencies())
                    .itemRewards(rewards)
                    .lootTableId(selectedLootTable)
                    .commandReward(command);

            if (xpAmount != null) builder.experienceAmount(xpAmount);
            else if (xpLevels != null) builder.experienceLevels(xpLevels);

            return builder.build();
        });

        return dialog.showAndWait();
    }

    private void populateBaseItemOptions(Quest quest,
                                         Map<String, ItemOption> optionIndex,
                                         ObservableList<ItemOption> options,
                                         Comparator<ItemOption> comparator,
                                         Map<String, EditableLootTable> lootTables) {
        VersionCatalog versionCatalog;
        try {
            versionCatalog = dev.ftbq.editor.services.UiServiceLocator.getVersionCatalog();
        } catch (Exception ex) {
            versionCatalog = null;
        }
        if (versionCatalog != null) {
            ItemCatalog vanilla = versionCatalog.getVanillaItems();
            if (vanilla != null) {
                for (ItemRef ref : vanilla.items()) {
                    ensureItemOption(ref.itemId(), null, optionIndex, options, comparator);
                }
            }
        }
        for (ItemReward reward : quest.itemRewards()) {
            ensureItemOption(reward.itemRef().itemId(), null, optionIndex, options, comparator);
        }
        for (EditableLootTable table : lootTables.values()) {
            if (table.iconId() != null && !table.iconId().isBlank()) {
                ensureItemOption(table.iconId(), null, optionIndex, options, comparator);
            }
            for (EditableLootItem item : table.items()) {
                ensureItemOption(item.itemId(), item.displayName(), optionIndex, options, comparator);
            }
        }
    }

    private List<ItemRewardRow> createItemRows(ObservableList<ItemOption> options,
                                               StringConverter<ItemOption> converter) {
        List<ItemRewardRow> rows = new ArrayList<>(MAX_ITEM_REWARDS);
        for (int i = 0; i < MAX_ITEM_REWARDS; i++) {
            rows.add(new ItemRewardRow(options, converter));
        }
        return rows;
    }

    private void applyExistingItemRewards(Quest quest,
                                          Map<String, ItemOption> optionIndex,
                                          ObservableList<ItemOption> options,
                                          Comparator<ItemOption> comparator,
                                          List<ItemRewardRow> rows) {
        List<ItemReward> rewards = quest.itemRewards();
        for (int i = 0; i < rewards.size() && i < rows.size(); i++) {
            ItemReward reward = rewards.get(i);
            ItemOption option = ensureItemOption(reward.itemRef().itemId(), null, optionIndex, options, comparator);
            rows.get(i).setItem(option, reward.itemRef().count());
        }
    }

    private Map<String, EditableLootTable> loadLootTables() {
        StoreDao storeDao = UiServiceLocator.storeDao;
        if (storeDao == null) {
            return Map.of();
        }
        Map<String, EditableLootTable> tables = new LinkedHashMap<>();
        for (StoreDao.LootTableEntity entity : storeDao.listLootTables()) {
            SnbtLootTableParser.LootTableData data = LOOT_TABLE_PARSER.parse(entity.data());
            List<EditableLootItem> items = new ArrayList<>();
            for (SnbtLootTableParser.LootTableItem item : data.items()) {
                items.add(new EditableLootItem(item.itemId(), item.displayName(), item.defaultCount(), item.weight()));
            }
            tables.put(entity.name(), new EditableLootTable(entity.name(), data.icon(), items));
        }
        return tables;
    }

    private ComboBox<String> createLootTableComboBox(ObservableList<String> values) {
        ComboBox<String> comboBox = new ComboBox<>(values);
        comboBox.setCellFactory(listView -> new StringListCell());
        comboBox.setButtonCell(new StringListCell());
        comboBox.setMaxWidth(Double.MAX_VALUE);
        return comboBox;
    }

    private ComboBox<EditableLootItem> createLootItemComboBox() {
        ComboBox<EditableLootItem> comboBox = new ComboBox<>();
        comboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(EditableLootItem item) {
                if (item == null) {
                    return "";
                }
                return item.displayLabel();
            }

            @Override
            public EditableLootItem fromString(String string) {
                return null;
            }
        });
        comboBox.setMaxWidth(Double.MAX_VALUE);
        return comboBox;
    }

    private StringConverter<ItemOption> createItemConverter(Map<String, ItemOption> optionIndex,
                                                            ObservableList<ItemOption> options,
                                                            Comparator<ItemOption> comparator) {
        return new StringConverter<>() {
            @Override
            public String toString(ItemOption item) {
                return item == null ? "" : item.displayLabel();
            }

            @Override
            public ItemOption fromString(String string) {
                if (string == null) {
                    return null;
                }
                String trimmed = string.trim();
                if (trimmed.isEmpty()) {
                    return null;
                }
                ItemOption existing = optionIndex.get(trimmed);
                if (existing != null) {
                    return existing;
                }
                if (trimmed.endsWith(")")) {
                    int open = trimmed.lastIndexOf('(');
                    int close = trimmed.lastIndexOf(')');
                    if (open >= 0 && close > open) {
                        String candidate = trimmed.substring(open + 1, close).trim();
                        if (!candidate.isEmpty()) {
                            ItemOption nested = optionIndex.get(candidate);
                            if (nested != null) {
                                return nested;
                            }
                            return ensureItemOption(candidate, null, optionIndex, options, comparator);
                        }
                    }
                }
                return ensureItemOption(trimmed, null, optionIndex, options, comparator);
            }
        };
    }

    private void saveActiveLootTableState(ComboBox<ItemOption> iconBox,
                                          Map<String, ItemOption> optionIndex,
                                          ObservableList<ItemOption> options,
                                          Comparator<ItemOption> comparator,
                                          ImageView iconPreview) {
        if (activeLootTable == null) {
            return;
        }
        ItemOption selection = iconBox.getValue();
        if (selection == null) {
            String text = iconBox.getEditor().getText();
            if (text != null && !text.isBlank()) {
                selection = ensureItemOption(text.trim(), null, optionIndex, options, comparator);
            }
        }
        String iconId = selection == null ? "" : selection.itemId();
        updateActiveLootTableIcon(iconId, iconPreview);
    }

    private void updateLootTableIconControls(EditableLootTable table,
                                             ComboBox<ItemOption> iconBox,
                                             ImageView iconPreview,
                                             Map<String, ItemOption> optionIndex,
                                             ObservableList<ItemOption> options,
                                             Comparator<ItemOption> comparator) {
        updatingLootTableIcon = true;
        try {
            if (table == null) {
                iconBox.setValue(null);
                iconBox.getEditor().setText("");
                iconPreview.setImage(null);
            } else {
                String iconId = table.iconId();
                ItemOption option = null;
                if (iconId != null && !iconId.isBlank()) {
                    option = ensureItemOption(iconId, null, optionIndex, options, comparator);
                }
                iconBox.setValue(option);
                if (option == null && iconId != null && !iconId.isBlank()) {
                    iconBox.getEditor().setText(iconId);
                }
                iconPreview.setImage(loadItemIcon(iconId).orElse(null));
            }
        } finally {
            updatingLootTableIcon = false;
        }
    }

    private void populateLootTableEntriesBox(EditableLootTable table, VBox container) {
        container.getChildren().clear();
        if (table == null || table.items().isEmpty()) {
            return;
        }
        for (EditableLootItem item : table.items()) {
            LootTableEntryRow row = new LootTableEntryRow(item);
            container.getChildren().add(row.node());
        }
    }

    private void updateActiveLootTableIcon(String iconId, ImageView iconPreview) {
        if (activeLootTable == null) {
            return;
        }
        String normalized = iconId == null ? "" : iconId;
        activeLootTable.setIconId(normalized);
        iconPreview.setImage(loadItemIcon(normalized).orElse(null));
    }

    private void persistLootTableChanges() {
        StoreDao storeDao = UiServiceLocator.storeDao;
        if (storeDao == null) {
            return;
        }
        for (EditableLootTable table : lootTables.values()) {
            if (!table.isDirty()) {
                continue;
            }
            String snbt = buildLootTableSnbt(table);
            storeDao.upsertLootTable(new StoreDao.LootTableEntity(table.name(), snbt));
        }
    }

    private String buildLootTableSnbt(EditableLootTable table) {
        StringBuilder builder = new StringBuilder();
        appendLootLine(builder, 0, "{");
        appendLootLine(builder, 1, "id:\"" + escape(table.name()) + "\",");
        String iconId = table.iconId() == null || table.iconId().isBlank() ? "minecraft:book" : table.iconId();
        appendLootLine(builder, 1, "icon:\"" + escape(iconId) + "\",");
        appendLootItems(builder, table.items());
        appendLootLine(builder, 0, "}");
        return builder.toString();
    }

    private void appendLootItems(StringBuilder builder, List<EditableLootItem> items) {
        if (items.isEmpty()) {
            appendLootLine(builder, 1, "items:[]");
            return;
        }
        appendLootLine(builder, 1, "items:[");
        for (int i = 0; i < items.size(); i++) {
            EditableLootItem item = items.get(i);
            String line = "{id:\"" + escape(item.itemId()) + "\", count:" + item.count()
                    + ", weight:" + item.weight() + "}";
            appendLootLine(builder, 2, line + (i == items.size() - 1 ? "" : ","));
        }
        appendLootLine(builder, 1, "]");
    }

    private void appendLootLine(StringBuilder builder, int indent, String line) {
        builder.append(SNBT_INDENT.repeat(indent)).append(line).append('\n');
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "\\\"");
    }

    private Optional<Image> loadItemIcon(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Optional.empty();
        }
        return itemIconCache.computeIfAbsent(itemId, this::resolveItemIcon);
    }

    private Optional<Image> resolveItemIcon(String itemId) {
        CacheManager cacheManager = UiServiceLocator.cacheManager;
        if (cacheManager == null) {
            return Optional.empty();
        }
        // Try direct icon by itemId
        Optional<Image> direct = cacheManager.fetchIcon(itemId).flatMap(this::toImage);
        if (direct.isPresent()) {
            return direct;
        }
        // Fallback via stored item -> icon hash -> icon bytes
        StoreDao storeDao = UiServiceLocator.storeDao;
        if (storeDao == null) {
            return Optional.empty();
        }
        return storeDao.findItemById(itemId)
                .flatMap(entity -> Optional.ofNullable(entity.iconHash()))
                .flatMap(cacheManager::fetchIcon)
                .flatMap(this::toImage);
    }

    // --- FIX: use a byte[] -> Optional<Image> converter and flatMap at call sites ---
    private Optional<Image> toImage(byte[] data) {
        if (data == null || data.length == 0) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Image(new ByteArrayInputStream(data)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
    // --- END FIX ---

    private ItemOption ensureItemOption(String itemId,
                                        String displayName,
                                        Map<String, ItemOption> optionIndex,
                                        ObservableList<ItemOption> options,
                                        Comparator<ItemOption> comparator) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        ItemOption existing = optionIndex.get(itemId);
        if (existing != null) {
            return existing;
        }
        String label = displayName;
        if (label == null || label.isBlank()) {
            label = ItemOption.deriveDisplayName(itemId);
        }
        ItemOption option = new ItemOption(itemId, label);
        optionIndex.put(itemId, option);
        options.add(option);
        FXCollections.sort(options, comparator);
        return option;
    }

    private int readSpinnerValue(Spinner<Integer> spinner) {
        spinner.increment(0);
        Integer value = spinner.getValue();
        return value == null ? 0 : Math.max(1, value);
    }

    private static final class EditableLootTable {
        private final String name;
        private final String originalIconId;
        private final List<EditableLootItem> items;
        private String iconId;

        private EditableLootTable(String name, String iconId, List<EditableLootItem> items) {
            this.name = Objects.requireNonNull(name, "name");
            this.originalIconId = iconId == null ? "" : iconId;
            this.iconId = this.originalIconId;
            this.items = new ArrayList<>(Objects.requireNonNull(items, "items"));
        }

        private String name() {
            return name;
        }

        private String iconId() {
            return iconId;
        }

        private void setIconId(String iconId) {
            this.iconId = iconId == null ? "" : iconId;
        }

        private List<EditableLootItem> items() {
            return items;
        }

        private boolean isDirty() {
            if (!Objects.equals(iconId, originalIconId)) {
                return true;
            }
            for (EditableLootItem item : items) {
                if (item.isDirty()) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class EditableLootItem {
        private final String itemId;
        private final String displayName;
        private final int count;
        private final int originalWeight;
        private int weight;

        private EditableLootItem(String itemId, String displayName, int count, int weight) {
            this.itemId = Objects.requireNonNull(itemId, "itemId");
            this.displayName = (displayName == null || displayName.isBlank())
                    ? ItemOption.deriveDisplayName(itemId)
                    : displayName;
            this.count = Math.max(1, count);
            this.weight = Math.max(1, weight);
            this.originalWeight = this.weight;
        }

        private String itemId() {
            return itemId;
        }

        private String displayName() {
            return displayName;
        }

        private String displayLabel() {
            return displayName + " (" + itemId + ")";
        }

        private int count() {
            return count;
        }

        private int weight() {
            return weight;
        }

        private int defaultCount() {
            return count;
        }

        private void setWeight(int weight) {
            this.weight = Math.max(1, weight);
        }

        private boolean isDirty() {
            return weight != originalWeight;
        }
    }

    private final class LootTableEntryRow {
        private final EditableLootItem item;
        private final HBox container;
        private final Spinner<Integer> weightSpinner;
        private final ImageView iconView;

        private LootTableEntryRow(EditableLootItem item) {
            this.item = Objects.requireNonNull(item, "item");
            this.iconView = new ImageView();
            iconView.setFitWidth(24);
            iconView.setFitHeight(24);
            iconView.setPreserveRatio(true);
            loadItemIcon(item.itemId()).ifPresent(iconView::setImage);

            Label nameLabel = new Label(item.displayLabel());
            Label countLabel = new Label("x" + item.count());
            Label weightLabel = new Label("Weight:");
            this.weightSpinner = new Spinner<>(new IntegerSpinnerValueFactory(1, Integer.MAX_VALUE, item.weight()));
            this.weightSpinner.setEditable(true);
            this.weightSpinner.valueProperty().addListener((obs, oldValue, newValue) -> {
                int value = newValue == null ? DEFAULT_LOOT_WEIGHT : Math.max(1, newValue);
                item.setWeight(value);
            });

            this.container = new HBox(8, iconView, nameLabel, countLabel, weightLabel, weightSpinner);
            this.container.setAlignment(Pos.CENTER_LEFT);
        }

        private Node node() {
            return container;
        }
    }

    private static final class ItemRewardRow {
        private final ComboBox<ItemOption> itemBox;
        private final Spinner<Integer> quantitySpinner;
        private final HBox container;
        private final StringConverter<ItemOption> converter;

        private ItemRewardRow(ObservableList<ItemOption> options, StringConverter<ItemOption> converter) {
            this.converter = converter;
            this.itemBox = new ComboBox<>(options);
            this.itemBox.setEditable(true);
            this.itemBox.setConverter(converter);
            this.itemBox.setPrefWidth(260);
            this.quantitySpinner = new Spinner<>(new IntegerSpinnerValueFactory(1, MAX_ITEM_COUNT, 1));
            this.quantitySpinner.setEditable(true);
            this.quantitySpinner.setPrefWidth(90);
            this.container = new HBox(8, itemBox, quantitySpinner);
        }

        private Node node() {
            return container;
        }

        private void setItem(ItemOption option, int count) {
            if (option == null) {
                itemBox.setValue(null);
                itemBox.getEditor().setText("");
            } else {
                itemBox.setValue(option);
            }
            quantitySpinner.getValueFactory().setValue(Math.max(1, count));
        }

        private Optional<ItemReward> toReward() {
            ItemOption option = itemBox.getValue();
            if (option == null) {
                String text = itemBox.getEditor().getText();
                if (text != null && !text.isBlank()) {
                    option = converter.fromString(text);
                    itemBox.setValue(option);
                }
            }
            if (option == null || option.itemId().isBlank()) {
                return Optional.empty();
            }
            Integer countValue = quantitySpinner.getValue();
            int count = countValue == null ? 1 : Math.max(1, countValue);
            return Optional.of(new ItemReward(new ItemRef(option.itemId(), count)));
        }

        private boolean isEmpty() {
            ItemOption value = itemBox.getValue();
            if (value != null && !value.itemId().isBlank()) {
                return false;
            }
            String editorText = itemBox.getEditor().getText();
            return editorText == null || editorText.isBlank();
        }
    }

    private static final class ItemOption {
        private final String itemId;
        private final String displayName;

        private ItemOption(String itemId, String displayName) {
            this.itemId = itemId;
            this.displayName = displayName;
        }

        private String itemId() {
            return itemId;
        }

        private String displayLabel() {
            String name = displayName == null || displayName.isBlank() ? itemId : displayName;
            return name + " (" + itemId + ")";
        }

        private static String deriveDisplayName(String itemId) {
            int colon = itemId.indexOf(':');
            String base = colon >= 0 ? itemId.substring(colon + 1) : itemId;
            String[] parts = base.split("[_ ]");
            StringBuilder builder = new StringBuilder();
            for (String part : parts) {
                if (part.isBlank()) {
                    continue;
                }
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(Character.toUpperCase(part.charAt(0)));
                if (part.length() > 1) {
                    builder.append(part.substring(1));
                }
            }
            String formatted = builder.toString();
            if (formatted.isBlank()) {
                formatted = itemId;
            }
            return formatted;
        }
    }

    private static final class StringListCell extends javafx.scene.control.ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
            } else if (item == null || item.isBlank()) {
                setText("None");
            } else {
                setText(item);
            }
        }
    }
}
