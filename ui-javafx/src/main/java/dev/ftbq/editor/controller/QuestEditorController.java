package dev.ftbq.editor.controller;

import dev.ftbq.editor.ThemeService;
import dev.ftbq.editor.domain.AdvancementTask;
import dev.ftbq.editor.domain.BiomeTask;
import dev.ftbq.editor.domain.CheckmarkTask;
import dev.ftbq.editor.domain.CustomTask;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.DimensionTask;
import dev.ftbq.editor.domain.FluidTask;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.ItemReward;
import dev.ftbq.editor.domain.ItemTask;
import dev.ftbq.editor.domain.KillTask;
import dev.ftbq.editor.domain.LocationTask;
import dev.ftbq.editor.domain.ObservationTask;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.RewardCommand;
import dev.ftbq.editor.domain.StageTask;
import dev.ftbq.editor.domain.StatTask;
import dev.ftbq.editor.domain.StructureTask;
import dev.ftbq.editor.domain.Task;
import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.domain.XpTask;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Controller backing the quest editor dialog.
 */
public final class QuestEditorController {

    public enum TaskType {
        ITEM("Item"),
        CHECKMARK("Checkmark"),
        ADVANCEMENT("Advancement"),
        LOCATION("Location"),
        KILL("Kill"),
        OBSERVATION("Observation"),
        GAMESTAGE("Game Stage"),
        DIMENSION("Dimension"),
        BIOME("Biome"),
        STRUCTURE("Structure"),
        XP("XP"),
        STAT("Stat"),
        FLUID("Fluid"),
        CUSTOM("Custom");

        private final String displayName;
        TaskType(String displayName) { this.displayName = displayName; }
        @Override public String toString() { return displayName; }
    }

    @FXML
    private Label questIdLabel;

    @FXML
    private TextField questNameField;

    @FXML
    private TextArea descriptionArea;

    @FXML
    private TextField iconIdField;

    @FXML
    private TextField iconPathField;

    @FXML
    private ComboBox<Visibility> visibilityCombo;

    @FXML
    private TextField xpAmountField;

    @FXML
    private TextField xpLevelsField;

    @FXML
    private TextField lootTableField;

    @FXML
    private TextField commandField;

    @FXML
    private ComboBox<String> commandRunAsCombo;

    @FXML
    private ListView<String> taskList;

    @FXML
    private ListView<String> rewardList;

    @FXML
    private ListView<String> dependencyList;

    @FXML
    private Button saveButton;

    private Quest quest;
    private Quest updatedQuest;
    private boolean saved = false;

    private final List<Task> editableTasks = new ArrayList<>();
    private final List<ItemReward> editableRewards = new ArrayList<>();
    private final List<Dependency> editableDependencies = new ArrayList<>();

    @FXML
    private void initialize() {
        if (descriptionArea != null) {
            descriptionArea.setWrapText(true);
        }
        if (visibilityCombo != null) {
            visibilityCombo.setItems(FXCollections.observableArrayList(Visibility.values()));
        }
        if (commandRunAsCombo != null) {
            commandRunAsCombo.setItems(FXCollections.observableArrayList("Server", "Player"));
        }

        if (taskList != null) {
            taskList.setItems(FXCollections.observableArrayList());
        }
        if (rewardList != null) {
            rewardList.setItems(FXCollections.observableArrayList());
        }
        if (dependencyList != null) {
            dependencyList.setItems(FXCollections.observableArrayList());
        }
    }

    @FXML
    private void onSave() {
        updatedQuest = buildQuestFromForm();
        saved = true;
        closeDialog();
    }

    @FXML
    private void onCancel() {
        saved = false;
        closeDialog();
    }

    private void closeDialog() {
        if (saveButton != null && saveButton.getScene() != null) {
            Stage stage = (Stage) saveButton.getScene().getWindow();
            stage.close();
        }
    }

    private Quest buildQuestFromForm() {
        String title = questNameField != null ? questNameField.getText() : quest.title();
        String description = descriptionArea != null ? descriptionArea.getText() : quest.description();
        String iconId = iconIdField != null ? iconIdField.getText() : quest.icon().icon();
        String iconPath = iconPathField != null ? iconPathField.getText() : quest.icon().relativePath().orElse(null);
        Visibility visibility = visibilityCombo != null && visibilityCombo.getValue() != null
                ? visibilityCombo.getValue()
                : quest.visibility();

        IconRef newIcon = iconPath != null && !iconPath.isBlank()
                ? new IconRef(iconId, java.util.Optional.of(iconPath))
                : new IconRef(iconId);

        Integer xpAmount = parseIntOrNull(xpAmountField);
        Integer xpLevels = parseIntOrNull(xpLevelsField);
        String lootTable = lootTableField != null && !lootTableField.getText().isBlank()
                ? lootTableField.getText().trim() : null;

        RewardCommand commandReward = null;
        if (commandField != null && !commandField.getText().isBlank()) {
            boolean runAsServer = commandRunAsCombo != null && "Server".equals(commandRunAsCombo.getValue());
            commandReward = new RewardCommand(commandField.getText().trim(), runAsServer);
        }

        return Quest.builder()
                .id(quest.id())
                .title(title)
                .description(description)
                .icon(newIcon)
                .visibility(visibility)
                .tasks(new ArrayList<>(editableTasks))
                .itemRewards(new ArrayList<>(editableRewards))
                .experienceAmount(xpAmount)
                .experienceLevels(xpLevels)
                .lootTableId(lootTable)
                .commandReward(commandReward)
                .dependencies(new ArrayList<>(editableDependencies))
                .build();
    }

    @FXML
    private void onAddTask() {
        Dialog<Task> dialog = new Dialog<>();
        dialog.setTitle("Add Task");
        dialog.setHeaderText("Select a task type and configure it");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(450);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        ComboBox<TaskType> taskTypeCombo = new ComboBox<>();
        taskTypeCombo.getItems().addAll(TaskType.values());
        taskTypeCombo.setValue(TaskType.ITEM);

        grid.add(new Label("Task Type:"), 0, 0);
        grid.add(taskTypeCombo, 1, 0);

        GridPane fieldsPane = new GridPane();
        fieldsPane.setHgap(10);
        fieldsPane.setVgap(10);
        grid.add(fieldsPane, 0, 1, 2, 1);

        TextField itemIdField = new TextField();
        TextField countField = new TextField("1");
        CheckBox consumeCheck = new CheckBox("Consume items");
        Button browseItemButton = new Button("Browse...");
        TextField advancementField = new TextField();
        TextField dimensionField = new TextField();
        TextField xField = new TextField("0");
        TextField yField = new TextField("64");
        TextField zField = new TextField("0");
        TextField radiusField = new TextField("10");
        TextField entityField = new TextField();
        TextField killCountField = new TextField("1");
        ComboBox<ObservationTask.ObserveType> observeTypeCombo = new ComboBox<>();
        observeTypeCombo.getItems().addAll(ObservationTask.ObserveType.values());
        observeTypeCombo.setValue(ObservationTask.ObserveType.ENTITY_TYPE);
        TextField toObserveField = new TextField();
        TextField timerField = new TextField("0");
        TextField stageField = new TextField();
        CheckBox teamStageCheck = new CheckBox("Team Stage");
        TextField biomeField = new TextField();
        TextField structureField = new TextField();
        TextField xpValueField = new TextField("100");
        CheckBox xpPointsCheck = new CheckBox("Points (not levels)");
        TextField statField = new TextField();
        TextField statValueField = new TextField("1");
        TextField fluidField = new TextField();
        TextField fluidAmountField = new TextField("1000");
        TextField maxProgressField = new TextField("1");

        Runnable updateFields = () -> {
            fieldsPane.getChildren().clear();
            TaskType selected = taskTypeCombo.getValue();
            int row = 0;
            switch (selected) {
                case ITEM -> {
                    HBox itemRow = new HBox(5);
                    itemIdField.setPromptText("e.g. minecraft:diamond");
                    HBox.setHgrow(itemIdField, Priority.ALWAYS);
                    itemRow.getChildren().addAll(itemIdField, browseItemButton);
                    fieldsPane.add(new Label("Item ID:"), 0, row);
                    fieldsPane.add(itemRow, 1, row++);
                    countField.setPromptText("Count");
                    fieldsPane.add(new Label("Count:"), 0, row);
                    fieldsPane.add(countField, 1, row++);
                    fieldsPane.add(consumeCheck, 1, row);
                }
                case CHECKMARK -> fieldsPane.add(new Label("No configuration needed"), 0, 0, 2, 1);
                case ADVANCEMENT -> {
                    advancementField.setPromptText("e.g. minecraft:story/mine_stone");
                    fieldsPane.add(new Label("Advancement ID:"), 0, row);
                    fieldsPane.add(advancementField, 1, row);
                }
                case LOCATION -> {
                    dimensionField.setPromptText("e.g. minecraft:overworld");
                    fieldsPane.add(new Label("Dimension:"), 0, row);
                    fieldsPane.add(dimensionField, 1, row++);
                    fieldsPane.add(new Label("X:"), 0, row);
                    fieldsPane.add(xField, 1, row++);
                    fieldsPane.add(new Label("Y:"), 0, row);
                    fieldsPane.add(yField, 1, row++);
                    fieldsPane.add(new Label("Z:"), 0, row);
                    fieldsPane.add(zField, 1, row++);
                    fieldsPane.add(new Label("Radius:"), 0, row);
                    fieldsPane.add(radiusField, 1, row);
                }
                case KILL -> {
                    entityField.setPromptText("e.g. minecraft:zombie");
                    fieldsPane.add(new Label("Entity Type:"), 0, row);
                    fieldsPane.add(entityField, 1, row++);
                    fieldsPane.add(new Label("Count:"), 0, row);
                    fieldsPane.add(killCountField, 1, row);
                }
                case OBSERVATION -> {
                    fieldsPane.add(new Label("Observe Type:"), 0, row);
                    fieldsPane.add(observeTypeCombo, 1, row++);
                    toObserveField.setPromptText("e.g. minecraft:zombie");
                    fieldsPane.add(new Label("To Observe:"), 0, row);
                    fieldsPane.add(toObserveField, 1, row++);
                    timerField.setPromptText("Ticks");
                    fieldsPane.add(new Label("Timer (ticks):"), 0, row);
                    fieldsPane.add(timerField, 1, row);
                }
                case GAMESTAGE -> {
                    stageField.setPromptText("e.g. iron_age");
                    fieldsPane.add(new Label("Stage:"), 0, row);
                    fieldsPane.add(stageField, 1, row++);
                    fieldsPane.add(teamStageCheck, 1, row);
                }
                case DIMENSION -> {
                    dimensionField.setPromptText("e.g. minecraft:the_nether");
                    fieldsPane.add(new Label("Dimension:"), 0, row);
                    fieldsPane.add(dimensionField, 1, row);
                }
                case BIOME -> {
                    biomeField.setPromptText("e.g. minecraft:plains");
                    fieldsPane.add(new Label("Biome:"), 0, row);
                    fieldsPane.add(biomeField, 1, row);
                }
                case STRUCTURE -> {
                    structureField.setPromptText("e.g. minecraft:village");
                    fieldsPane.add(new Label("Structure:"), 0, row);
                    fieldsPane.add(structureField, 1, row);
                }
                case XP -> {
                    fieldsPane.add(new Label("XP Value:"), 0, row);
                    fieldsPane.add(xpValueField, 1, row++);
                    fieldsPane.add(xpPointsCheck, 1, row);
                }
                case STAT -> {
                    statField.setPromptText("e.g. minecraft:walk_one_cm");
                    fieldsPane.add(new Label("Stat:"), 0, row);
                    fieldsPane.add(statField, 1, row++);
                    fieldsPane.add(new Label("Value:"), 0, row);
                    fieldsPane.add(statValueField, 1, row);
                }
                case FLUID -> {
                    fluidField.setPromptText("e.g. minecraft:water");
                    fieldsPane.add(new Label("Fluid:"), 0, row);
                    fieldsPane.add(fluidField, 1, row++);
                    fieldsPane.add(new Label("Amount (mB):"), 0, row);
                    fieldsPane.add(fluidAmountField, 1, row);
                }
                case CUSTOM -> {
                    fieldsPane.add(new Label("Max Progress:"), 0, row);
                    fieldsPane.add(maxProgressField, 1, row);
                }
            }
            dialog.getDialogPane().getScene().getWindow().sizeToScene();
        };

        taskTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> updateFields.run());
        updateFields.run();

        browseItemButton.setOnAction(e -> openItemBrowser(itemIdField));

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            TaskType selected = taskTypeCombo.getValue();
            try {
                return switch (selected) {
                    case ITEM -> {
                        String itemId = itemIdField.getText().trim();
                        if (itemId.isEmpty()) yield null;
                        int count = parseIntSafe(countField.getText(), 1);
                        yield new ItemTask(new ItemRef(itemId, count), consumeCheck.isSelected());
                    }
                    case CHECKMARK -> new CheckmarkTask();
                    case ADVANCEMENT -> {
                        String adv = advancementField.getText().trim();
                        if (adv.isEmpty()) yield null;
                        yield new AdvancementTask(adv);
                    }
                    case LOCATION -> new LocationTask(
                            dimensionField.getText().trim(),
                            parseDoubleSafe(xField.getText(), 0),
                            parseDoubleSafe(yField.getText(), 64),
                            parseDoubleSafe(zField.getText(), 0),
                            parseDoubleSafe(radiusField.getText(), 10)
                    );
                    case KILL -> {
                        String entity = entityField.getText().trim();
                        if (entity.isEmpty()) yield null;
                        yield new KillTask(entity, parseLongSafe(killCountField.getText(), 1), null, null);
                    }
                    case OBSERVATION -> new ObservationTask(
                            observeTypeCombo.getValue(),
                            toObserveField.getText().trim(),
                            parseLongSafe(timerField.getText(), 0)
                    );
                    case GAMESTAGE -> {
                        String stage = stageField.getText().trim();
                        if (stage.isEmpty()) yield null;
                        yield new StageTask(stage, teamStageCheck.isSelected());
                    }
                    case DIMENSION -> {
                        String dim = dimensionField.getText().trim();
                        if (dim.isEmpty()) yield null;
                        yield new DimensionTask(dim);
                    }
                    case BIOME -> {
                        String biome = biomeField.getText().trim();
                        if (biome.isEmpty()) yield null;
                        yield new BiomeTask(biome);
                    }
                    case STRUCTURE -> {
                        String struct = structureField.getText().trim();
                        if (struct.isEmpty()) yield null;
                        yield new StructureTask(struct);
                    }
                    case XP -> new XpTask(parseLongSafe(xpValueField.getText(), 100), xpPointsCheck.isSelected());
                    case STAT -> {
                        String stat = statField.getText().trim();
                        if (stat.isEmpty()) yield null;
                        yield new StatTask(stat, parseIntSafe(statValueField.getText(), 1));
                    }
                    case FLUID -> {
                        String fluid = fluidField.getText().trim();
                        if (fluid.isEmpty()) yield null;
                        yield new FluidTask(fluid, parseLongSafe(fluidAmountField.getText(), 1000));
                    }
                    case CUSTOM -> new CustomTask(parseLongSafe(maxProgressField.getText(), 1));
                };
            } catch (Exception ex) {
                return null;
            }
        });

        dialog.showAndWait().ifPresent(task -> {
            editableTasks.add(task);
            refreshTaskList();
        });
    }

    private void openItemBrowser(TextField targetField) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/dev/ftbq/editor/view/item_browser.fxml"));
            Parent root = loader.load();
            ItemBrowserController controller = loader.getController();
            controller.setOnItemSelected(entity -> {
                if (entity != null) {
                    targetField.setText(entity.id());
                }
            });
            Stage stage = new Stage();
            stage.setTitle("Select Item");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root, 800, 600));
            ThemeService.getInstance().registerStage(stage);
            stage.show();
            stage.setOnHidden(event -> controller.dispose());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    private int parseIntSafe(String text, int defaultValue) {
        try { return Integer.parseInt(text.trim()); } catch (NumberFormatException e) { return defaultValue; }
    }

    private long parseLongSafe(String text, long defaultValue) {
        try { return Long.parseLong(text.trim()); } catch (NumberFormatException e) { return defaultValue; }
    }

    private double parseDoubleSafe(String text, double defaultValue) {
        try { return Double.parseDouble(text.trim()); } catch (NumberFormatException e) { return defaultValue; }
    }

    @FXML
    private void onRemoveTask() {
        int index = taskList.getSelectionModel().getSelectedIndex();
        if (index >= 0 && index < editableTasks.size()) {
            editableTasks.remove(index);
            refreshTaskList();
        }
    }

    @FXML
    private void onAddReward() {
        Dialog<ItemReward> dialog = new Dialog<>();
        dialog.setTitle("Add Reward");
        dialog.setHeaderText("Add an item reward");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        dialog.getDialogPane().setPrefWidth(400);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        TextField itemIdField = new TextField();
        itemIdField.setPromptText("e.g. minecraft:diamond");
        Button browseButton = new Button("Browse...");
        browseButton.setOnAction(e -> openItemBrowser(itemIdField));

        HBox itemRow = new HBox(5);
        HBox.setHgrow(itemIdField, Priority.ALWAYS);
        itemRow.getChildren().addAll(itemIdField, browseButton);

        TextField countField = new TextField("1");
        countField.setPromptText("Count");

        grid.add(new Label("Item ID:"), 0, 0);
        grid.add(itemRow, 1, 0);
        grid.add(new Label("Count:"), 0, 1);
        grid.add(countField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                String itemId = itemIdField.getText().trim();
                if (itemId.isEmpty()) return null;
                int count = parseIntSafe(countField.getText(), 1);
                return new ItemReward(new ItemRef(itemId, count));
            }
            return null;
        });

        dialog.showAndWait().ifPresent(reward -> {
            editableRewards.add(reward);
            refreshRewardList();
        });
    }

    @FXML
    private void onRemoveReward() {
        int index = rewardList.getSelectionModel().getSelectedIndex();
        if (index >= 0 && index < editableRewards.size()) {
            editableRewards.remove(index);
            refreshRewardList();
        }
    }

    @FXML
    private void onAddDependency() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Dependency");
        dialog.setHeaderText("Add a quest dependency");
        dialog.setContentText("Quest ID:");

        dialog.showAndWait().ifPresent(questId -> {
            if (!questId.isBlank()) {
                editableDependencies.add(new Dependency(questId.trim(), true));
                refreshDependencyList();
            }
        });
    }

    @FXML
    private void onRemoveDependency() {
        int index = dependencyList.getSelectionModel().getSelectedIndex();
        if (index >= 0 && index < editableDependencies.size()) {
            editableDependencies.remove(index);
            refreshDependencyList();
        }
    }

    private void refreshTaskList() {
        if (taskList != null) {
            taskList.getItems().setAll(editableTasks.stream()
                    .map(this::describeTask)
                    .collect(Collectors.toList()));
        }
    }

    private void refreshRewardList() {
        if (rewardList != null) {
            rewardList.getItems().setAll(editableRewards.stream()
                    .map(ItemReward::describe)
                    .collect(Collectors.toList()));
        }
    }

    private void refreshDependencyList() {
        if (dependencyList != null) {
            dependencyList.getItems().setAll(editableDependencies.stream()
                    .map(this::describeDependency)
                    .collect(Collectors.toList()));
        }
    }

    private Integer parseIntOrNull(TextField field) {
        if (field == null || field.getText() == null || field.getText().isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public boolean isSaved() {
        return saved;
    }

    public Quest getUpdatedQuest() {
        return updatedQuest;
    }

    public void setQuest(Quest quest) {
        this.quest = Objects.requireNonNull(quest, "quest");

        if (questIdLabel != null) {
            questIdLabel.setText(quest.id());
        }
        if (questNameField != null) {
            questNameField.setText(quest.title());
        }
        if (descriptionArea != null) {
            descriptionArea.setText(quest.description());
        }

        IconRef icon = quest.icon();
        if (iconIdField != null) {
            iconIdField.setText(icon.icon());
        }
        if (iconPathField != null) {
            iconPathField.setText(icon.relativePath().orElse(""));
        }

        if (visibilityCombo != null) {
            visibilityCombo.setValue(quest.visibility());
        }
        if (xpAmountField != null) {
            xpAmountField.setText(quest.experienceAmountOptional().map(String::valueOf).orElse(""));
        }
        if (xpLevelsField != null) {
            xpLevelsField.setText(quest.experienceLevelsOptional().map(String::valueOf).orElse(""));
        }
        if (lootTableField != null) {
            lootTableField.setText(quest.lootTableIdOptional().orElse(""));
        }

        RewardCommand commandReward = quest.commandReward();
        if (commandField != null) {
            commandField.setText(commandReward != null ? commandReward.command() : "");
        }
        if (commandRunAsCombo != null) {
            commandRunAsCombo.setValue(commandReward == null || commandReward.runAsServer() ? "Server" : "Player");
        }

        editableTasks.clear();
        editableTasks.addAll(quest.tasks());
        editableRewards.clear();
        editableRewards.addAll(quest.itemRewards());
        editableDependencies.clear();
        editableDependencies.addAll(quest.dependencies());

        refreshTaskList();
        refreshRewardList();
        refreshDependencyList();
    }

    private String describeTask(Task task) {
        if (task == null) {
            return "Unknown task";
        }
        return task.describe();
    }

    private String describeDependency(Dependency dependency) {
        if (dependency == null) {
            return "Unknown dependency";
        }
        String suffix = dependency.required() ? "required" : "optional";
        return dependency.questId() + " (" + suffix + ")";
    }

    public Quest getQuest() {
        return quest;
    }
}
