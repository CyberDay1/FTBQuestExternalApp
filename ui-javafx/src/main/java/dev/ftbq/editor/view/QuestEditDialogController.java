package dev.ftbq.editor.view;

import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.ItemReward;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.RewardCommand;
import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.domain.version.ItemCatalog;
import dev.ftbq.editor.domain.version.VersionCatalog;
import dev.ftbq.editor.io.snbt.SnbtLootTableParser;
import dev.ftbq.editor.io.snbt.SnbtLootTableParser.LootTableItem;
import dev.ftbq.editor.services.UiServiceLocator;
import dev.ftbq.editor.store.StoreDao;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Presents a dialog for editing quest details, including expanded reward configuration.
 */
public final class QuestEditDialogController {

    private static final int MAX_ITEM_REWARDS = 5;
    private static final int MAX_ITEM_COUNT = 999;
    private static final SnbtLootTableParser LOOT_TABLE_PARSER = new SnbtLootTableParser();

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

        Map<String, List<LootTableItem>> lootItemsByTable = loadLootTableItems();
        ObservableList<String> lootTableNames = FXCollections.observableArrayList(lootItemsByTable.keySet());
        lootTableNames.add(0, "");
        ComboBox<String> lootTableBox = createLootTableComboBox(lootTableNames);
        String questLootTable = quest.lootTableId();
        if (questLootTable != null && !questLootTable.isBlank() && !lootTableNames.contains(questLootTable)) {
            lootTableNames.add(questLootTable);
        }
        lootTableBox.setValue(questLootTable != null ? questLootTable : "");

        ObservableList<ItemOption> itemOptions = FXCollections.observableArrayList();
        Map<String, ItemOption> optionIndex = new LinkedHashMap<>();
        Comparator<ItemOption> optionComparator = Comparator.comparing(ItemOption::displayLabel, String.CASE_INSENSITIVE_ORDER);
        populateBaseItemOptions(quest, optionIndex, itemOptions, optionComparator, lootItemsByTable);

        StringConverter<ItemOption> itemConverter = createItemConverter(optionIndex, itemOptions, optionComparator);
        List<ItemRewardRow> itemRows = createItemRows(itemOptions, itemConverter);
        applyExistingItemRewards(quest, optionIndex, itemOptions, optionComparator, itemRows);

        ComboBox<LootTableItem> lootItemBox = createLootItemComboBox();
        Button addLootItemButton = new Button("Add Item to Rewards");
        addLootItemButton.setDisable(true);
        lootItemBox.setDisable(true);

        lootTableBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            List<LootTableItem> items = lootItemsByTable.getOrDefault(newValue, List.of());
            lootItemBox.setItems(FXCollections.observableArrayList(items));
            boolean hasItems = !items.isEmpty();
            lootItemBox.setDisable(!hasItems);
            addLootItemButton.setDisable(!hasItems);
            if (hasItems) {
                lootItemBox.getSelectionModel().selectFirst();
            }
        });

        addLootItemButton.setOnAction(event -> {
            LootTableItem selected = lootItemBox.getValue();
            if (selected == null) {
                return;
            }
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
        if (quest.commandReward() != null) {
            commandField.setText(quest.commandReward().command());
        }

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
        grid.add(new Label("Loot items"), 0, 6);
        grid.add(lootItemControls, 1, 6);
        grid.add(new Label("Item rewards"), 0, 7);
        grid.add(itemSection, 1, 7);
        grid.add(new Label("XP reward"), 0, 8);
        grid.add(xpSection, 1, 8);
        grid.add(new Label("Command reward"), 0, 9);
        grid.add(commandField, 1, 9);

        GridPane.setHgrow(titleField, Priority.ALWAYS);
        GridPane.setHgrow(descriptionArea, Priority.ALWAYS);
        GridPane.setHgrow(iconField, Priority.ALWAYS);
        GridPane.setHgrow(lootTableBox, Priority.ALWAYS);
        GridPane.setHgrow(itemSection, Priority.ALWAYS);
        GridPane.setHgrow(commandField, Priority.ALWAYS);

        dialog.getDialogPane().setContent(grid);

        Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.setDisable(titleField.getText() == null || titleField.getText().isBlank());
        titleField.textProperty().addListener((obs, oldValue, newValue) ->
                saveButton.setDisable(newValue == null || newValue.isBlank()));

        dialog.setResultConverter(button -> {
            if (button != saveButtonType) {
                return null;
            }
            String title = titleField.getText() == null ? "" : titleField.getText().trim();
            String description = descriptionArea.getText() == null ? "" : descriptionArea.getText().trim();
            String iconText = iconField.getText() == null ? "" : iconField.getText().trim();
            Visibility visibility = visibilityBox.getValue() == null ? Visibility.VISIBLE : visibilityBox.getValue();

            List<ItemReward> rewards = new ArrayList<>();
            for (ItemRewardRow row : itemRows) {
                row.toReward().ifPresent(rewards::add);
            }

            Integer xpAmount = null;
            Integer xpLevels = null;
            if (xpToggle.getSelectedToggle() == xpAmountButton) {
                xpAmount = readSpinnerValue(xpAmountSpinner);
            } else if (xpToggle.getSelectedToggle() == xpLevelsButton) {
                xpLevels = readSpinnerValue(xpLevelSpinner);
            }

            String selectedLootTable = lootTableBox.getValue();
            if (selectedLootTable != null && selectedLootTable.isBlank()) {
                selectedLootTable = null;
            }

            RewardCommand command = null;
            String commandText = commandField.getText();
            if (commandText != null && !commandText.isBlank()) {
                command = new RewardCommand(commandText.trim(), false);
            }

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

            if (xpAmount != null) {
                builder.experienceAmount(xpAmount);
            } else if (xpLevels != null) {
                builder.experienceLevels(xpLevels);
            }

            return builder.build();
        });

        return dialog.showAndWait();
    }

    private void populateBaseItemOptions(Quest quest,
                                         Map<String, ItemOption> optionIndex,
                                         ObservableList<ItemOption> options,
                                         Comparator<ItemOption> comparator,
                                         Map<String, List<LootTableItem>> lootItems) {
        VersionCatalog versionCatalog;
        try {
            versionCatalog = dev.ftbq.editor.support.UiServiceLocator.getVersionCatalog();
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
        lootItems.values().stream().flatMap(List::stream)
                .forEach(item -> ensureItemOption(item.itemId(), item.displayName(), optionIndex, options, comparator));
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

    private Map<String, List<LootTableItem>> loadLootTableItems() {
        StoreDao storeDao = UiServiceLocator.storeDao;
        if (storeDao == null) {
            return Map.of();
        }
        Map<String, List<LootTableItem>> items = new LinkedHashMap<>();
        for (StoreDao.LootTableEntity entity : storeDao.listLootTables()) {
            List<LootTableItem> parsed = LOOT_TABLE_PARSER.parseItems(entity.data());
            items.put(entity.name(), parsed);
        }
        return items;
    }

    private ComboBox<String> createLootTableComboBox(ObservableList<String> values) {
        ComboBox<String> comboBox = new ComboBox<>(values);
        comboBox.setCellFactory(listView -> new StringListCell());
        comboBox.setButtonCell(new StringListCell());
        comboBox.setMaxWidth(Double.MAX_VALUE);
        return comboBox;
    }

    private ComboBox<LootTableItem> createLootItemComboBox() {
        ComboBox<LootTableItem> comboBox = new ComboBox<>();
        comboBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(LootTableItem item) {
                if (item == null) {
                    return "";
                }
                return item.displayName() + " (" + item.itemId() + ")";
            }

            @Override
            public LootTableItem fromString(String string) {
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
