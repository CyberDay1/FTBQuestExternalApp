package dev.ftbq.editor.controller;

import dev.ftbq.editor.ThemeService;
import dev.ftbq.editor.assets.CacheManager;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.ItemTask;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.Reward;
import dev.ftbq.editor.domain.RewardCommand;
import dev.ftbq.editor.domain.RewardType;
import dev.ftbq.editor.domain.Task;
import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.services.UiServiceLocator;
import dev.ftbq.editor.services.bus.CommandBus;
import dev.ftbq.editor.services.bus.EventBus;
import dev.ftbq.editor.services.bus.ServiceLocator;
import dev.ftbq.editor.services.bus.UndoManager;
import dev.ftbq.editor.services.logging.StructuredLogger;
import dev.ftbq.editor.viewmodel.QuestEditorViewModel;
import dev.ftbq.editor.controller.ItemBrowserController;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Updated quest editor controller.
 *
 * This version enhances the icon handling by loading the actual icon image
 * from the cache into the ImageView and sets up accessible text properly.
 */
public class QuestEditorController {
    @FXML
    private BorderPane rootPane;

    // Buttons enabling users to add dependencies, tasks, and rewards to the quest.
    @FXML
    private Button addDependencyButton;
    @FXML
    private Button addTaskButton;
    @FXML
    private Button addRewardButton;

    @FXML
    private TextField questTitleField;

    @FXML
    private TextArea questDescriptionArea;

    @FXML
    private Button chooseIconButton;

    @FXML
    private ImageView questIconView;

    @FXML
    private Button linkLootTableButton;

    @FXML
    private ListView<Dependency> dependencyListView;

    @FXML
    private ListView<Task> taskListView;

    @FXML
    private ListView<Reward> rewardListView;

    @FXML
    private Button saveButton;

    @FXML
    private Button cancelButton;

    @FXML
    private Button undoButton;

    @FXML
    private Button redoButton;

    private final QuestEditorViewModel viewModel;
    private final StructuredLogger logger;

    private final Tooltip iconTooltip = new Tooltip();
    private Consumer<String> lootTableLinkHandler = tableId -> { };
    private UndoManager undoManager;
    private CommandBus commandBus;
    private boolean undoStateListenerRegistered;

    public QuestEditorController() {
        this(
                new QuestEditorViewModel(ServiceLocator.commandBus(), ServiceLocator.eventBus(), ServiceLocator.undoManager()),
                ServiceLocator.loggerFactory().create(QuestEditorController.class)
        );
        this.commandBus = ServiceLocator.commandBus();
        this.undoManager = ServiceLocator.undoManager();
    }

    QuestEditorController(QuestEditorViewModel viewModel) {
        this(viewModel, ServiceLocator.loggerFactory().create(QuestEditorController.class));
    }

    QuestEditorController(QuestEditorViewModel viewModel, StructuredLogger logger) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void setCommandBus(CommandBus commandBus) {
        this.commandBus = Objects.requireNonNull(commandBus, "commandBus");
        viewModel.setCommandBus(this.commandBus);
        undoStateListenerRegistered = false;
        registerUndoStateListener();
    }

    public void setEventBus(EventBus eventBus) {
        viewModel.setEventBus(Objects.requireNonNull(eventBus, "eventBus"));
    }

    public void setUndoManager(UndoManager undoManager) {
        this.undoManager = Objects.requireNonNull(undoManager, "undoManager");
        viewModel.setUndoManager(undoManager);
        updateUndoRedoButtons();
    }

    public void setLootTableLinkHandler(Consumer<String> handler) {
        lootTableLinkHandler = handler == null ? tableId -> { } : handler;
    }

    @FXML
    public void initialize() {
        logger.info("Quest editor view initialised");
        attachStylesheet();
        bindFields();
        configureListViews();
        configureAddButtons();
        installUndoRedoShortcuts();
        if (questDescriptionArea != null) {
            questDescriptionArea.setWrapText(true);
        }
        if (chooseIconButton != null) {
            chooseIconButton.setTooltip(iconTooltip);
            if (chooseIconButton.getAccessibleText() == null) {
                chooseIconButton.setAccessibleText("Choose quest icon");
            }
        }
        if (linkLootTableButton != null && linkLootTableButton.getAccessibleText() == null) {
            linkLootTableButton.setAccessibleText("Link loot table");
        }
        if (saveButton != null && saveButton.getAccessibleText() == null) {
            saveButton.setAccessibleText("Save quest");
        }
        if (cancelButton != null && cancelButton.getAccessibleText() == null) {
            cancelButton.setAccessibleText("Cancel quest edits");
        }
        if (undoButton != null && undoButton.getAccessibleText() == null) {
            undoButton.setAccessibleText("Undo last change");
        }
        if (redoButton != null && redoButton.getAccessibleText() == null) {
            redoButton.setAccessibleText("Redo change");
        }
        IconRef initialIcon = viewModel.iconProperty().get();
        updateIconTooltip(initialIcon);
        viewModel.iconProperty().addListener((obs, oldIcon, newIcon) -> updateIconTooltip(newIcon));
        if (viewModel.getCurrentQuest() == null) {
            loadQuest(createDefaultQuest());
        } else {
            refreshFromViewModel();
        }
        registerUndoStateListener();
        updateUndoRedoButtons();
    }

    private void installUndoRedoShortcuts() {
        if (rootPane != null) {
            rootPane.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPressed);
        }
    }

    private void handleKeyPressed(KeyEvent event) {
        if (!event.isShortcutDown()) {
            return;
        }
        if (event.getCode() == KeyCode.Z) {
            if (undoManager != null && undoManager.undo()) {
                event.consume();
                updateUndoRedoButtons();
            }
        } else if (event.getCode() == KeyCode.Y) {
            if (undoManager != null && undoManager.redo()) {
                event.consume();
                updateUndoRedoButtons();
            }
        }
    }

    private void attachStylesheet() {
        if (rootPane != null) {
            rootPane.getStylesheets().add(
                    Objects.requireNonNull(
                            getClass().getResource("/dev/ftbq/editor/view/quest_editor.css"),
                            "quest_editor.css must be available"
                    ).toExternalForm()
            );
        }
    }

    private void bindFields() {
        if (questTitleField != null) {
            questTitleField.textProperty().bindBidirectional(viewModel.titleProperty());
        }
        if (questDescriptionArea != null) {
            questDescriptionArea.textProperty().bindBidirectional(viewModel.descriptionProperty());
        }
    }

    private void configureListViews() {
        if (dependencyListView != null) {
            dependencyListView.setItems(viewModel.getDependencies());
            dependencyListView.setCellFactory(list -> new ListCell<>() {
                @Override
                protected void updateItem(Dependency item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.questId() + (item.required() ? " (required)" : ""));
                }
            });
        }

        if (taskListView != null) {
            taskListView.setItems(viewModel.getTasks());
            taskListView.setCellFactory(list -> new ListCell<>() {
                @Override
                protected void updateItem(Task item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.type());
                }
            });
        }

        if (rewardListView != null) {
            rewardListView.setItems(viewModel.getRewards());
            rewardListView.setCellFactory(list -> new ListCell<>() {
                @Override
                protected void updateItem(Reward item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : formatReward(item));
                }
            });
        }
    }

    private void configureAddButtons() {
        if (addDependencyButton != null) {
            addDependencyButton.setOnAction(event -> onAddDependency());
            if (addDependencyButton.getAccessibleText() == null) {
                addDependencyButton.setAccessibleText("Add quest dependency");
            }
        }
        if (addTaskButton != null) {
            addTaskButton.setOnAction(event -> onAddTask());
            if (addTaskButton.getAccessibleText() == null) {
                addTaskButton.setAccessibleText("Add quest task");
            }
        }
        if (addRewardButton != null) {
            addRewardButton.setOnAction(event -> onAddReward());
            if (addRewardButton.getAccessibleText() == null) {
                addRewardButton.setAccessibleText("Add quest reward");
            }
        }
    }

    /**
     * Updates the tooltip and icon view based on the provided icon reference.
     *
     * When an icon is selected, this method fetches the corresponding icon
     * bytes from the cache using {@link CacheManager} and displays the image
     * in the {@link ImageView}. It also updates the accessible text and
     * visibility of the icon view.
     *
     * @param icon the selected icon, or null if none is selected
     */
    private void updateIconTooltip(IconRef icon) {
        if (icon == null) {
            iconTooltip.setText("No icon selected");
        } else {
            iconTooltip.setText("Icon: " + icon.icon());
        }
        if (questIconView != null) {
            if (icon == null) {
                questIconView.setImage(null);
            } else {
                CacheManager cacheManager = UiServiceLocator.cacheManager;
                if (cacheManager != null) {
                    cacheManager.fetchIcon(icon.icon()).ifPresentOrElse(
                            bytes -> questIconView.setImage(new Image(new ByteArrayInputStream(bytes))),
                            () -> questIconView.setImage(null)
                    );
                } else {
                    questIconView.setImage(null);
                }
            }
            // Update accessibility and visibility
            questIconView.setAccessibleText(icon == null ? "No icon selected" : icon.icon());
            questIconView.setManaged(icon != null);
            questIconView.setVisible(icon != null);
        }
    }

    private Quest createDefaultQuest() {
        return Quest.builder()
                .id("quest-" + System.currentTimeMillis())
                .title("New Quest")
                .description("")
                .icon(new IconRef("minecraft:book"))
                .visibility(Visibility.VISIBLE)
                .build();
    }

    public void loadQuest(Quest quest) {
        if (viewModel != null) {
            viewModel.loadQuest(quest);
            refreshFromViewModel();
        }
    }

    private void refreshFromViewModel() {
        if (questTitleField != null) {
            questTitleField.positionCaret(questTitleField.getText().length());
        }
        if (questDescriptionArea != null) {
            questDescriptionArea.positionCaret(questDescriptionArea.getText().length());
        }
    }

    @FXML
    private void onChooseIconButton() {
        logger.debug("Opening item browser for quest icon selection");
        openItemBrowser();
    }

    @FXML
    private void onLinkLootTable() {
        String tableId;
        if (viewModel.getCurrentQuest() != null) {
            tableId = viewModel.getCurrentQuest().id();
        } else {
            tableId = viewModel.titleProperty().get();
        }
        logger.debug("Linking quest to loot table", StructuredLogger.field("questId", tableId));
        lootTableLinkHandler.accept(tableId == null ? "" : tableId);
    }

    @FXML
    private void handleSave() {
        if (!validateQuestConfiguration()) {
            return;
        }
        Quest saved = viewModel.save();
        logger.info("Quest saved", StructuredLogger.field("questId", saved.id()));
    }

    @FXML
    private void handleCancel() {
        viewModel.revertChanges();
        logger.info("Quest edits reverted", StructuredLogger.field("questId", viewModel.getCurrentQuest() != null ? viewModel.getCurrentQuest().id() : ""));
        refreshFromViewModel();
        updateUndoRedoButtons();
    }

    @FXML
    private void handleUndoAction() {
        if (undoManager != null && undoManager.undo()) {
            updateUndoRedoButtons();
        }
    }

    @FXML
    private void handleRedoAction() {
        if (undoManager != null && undoManager.redo()) {
            updateUndoRedoButtons();
        }
    }

    private void onAddDependency() {
        // Build a dialog that lets the user pick from existing quests instead of typing an ID.
        Dialog<Dependency> dialog = new Dialog<>();
        dialog.setTitle("Add dependency");
        dialog.setHeaderText("Select quest dependency");
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        // Load existing quests from the store. Fallback to empty list on error.
        List<Quest> quests = new ArrayList<>();
        if (UiServiceLocator.storeDao != null) {
            try {
                quests = UiServiceLocator.storeDao.listQuests();
            } catch (Exception ex) {
                logger.error("Failed to load quests for dependency selection", ex);
            }
        }

        ListView<Quest> questList = new ListView<>();
        questList.getItems().addAll(quests);
        questList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Quest item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.id() + " - " + item.title());
            }
        });
        CheckBox requiredBox = new CheckBox("Required dependency");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new javafx.scene.control.Label("Quest:"), 0, 0);
        grid.add(questList, 1, 0);
        grid.add(requiredBox, 1, 1);

        dialog.getDialogPane().setContent(grid);

        Node addButton = dialog.getDialogPane().lookupButton(addButtonType);
        addButton.setDisable(true);
        questList.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldSel, newSel) -> addButton.setDisable(newSel == null));

        configureDialogOwner(dialog);
        dialog.setResultConverter(button -> {
            if (button != addButtonType) {
                return null;
            }
            Quest selected = questList.getSelectionModel().getSelectedItem();
            if (selected == null) {
                return null;
            }
            return new Dependency(selected.id(), requiredBox.isSelected());
        });
        dialog.showAndWait().ifPresent(viewModel.getDependencies()::add);
    }

    private void onAddTask() {
        Dialog<Task> dialog = new Dialog<>();
        dialog.setTitle("Add task");
        dialog.setHeaderText("Create an item submission task");
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        TextField itemIdField = new TextField();
        itemIdField.setPromptText("namespace:item");
        Spinner<Integer> countSpinner = new Spinner<>();
        countSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, Integer.MAX_VALUE, 1));
        CheckBox consumeBox = new CheckBox("Consume items");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new javafx.scene.control.Label("Item ID:"), 0, 0);
        grid.add(itemIdField, 1, 0);
        grid.add(new javafx.scene.control.Label("Count:"), 0, 1);
        grid.add(countSpinner, 1, 1);
        grid.add(consumeBox, 1, 2);

        dialog.getDialogPane().setContent(grid);

        Node addButton = dialog.getDialogPane().lookupButton(addButtonType);
        addButton.setDisable(true);
        itemIdField.textProperty().addListener((obs, oldValue, newValue) -> addButton.setDisable(newValue == null || newValue.isBlank()));

        configureDialogOwner(dialog);
        dialog.setResultConverter(button -> {
            if (button != addButtonType) {
                return null;
            }
            ItemRef ref = new ItemRef(itemIdField.getText().trim(), countSpinner.getValue());
            return new ItemTask(ref, consumeBox.isSelected());
        });
        dialog.showAndWait().ifPresent(viewModel.getTasks()::add);
    }

    private void onAddReward() {
        Dialog<Reward> dialog = new Dialog<>();
        dialog.setTitle("Add reward");
        dialog.setHeaderText("Create a quest reward");
        ButtonType addButtonType = new ButtonType("Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButtonType, ButtonType.CANCEL);

        ComboBox<RewardType> typeBox = new ComboBox<>();
        typeBox.getItems().setAll(RewardType.values());
        typeBox.getSelectionModel().select(RewardType.ITEM);

        TextField itemIdField = new TextField();
        itemIdField.setPromptText("namespace:item");
        Spinner<Integer> countSpinner = new Spinner<>();
        countSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, Integer.MAX_VALUE, 1));

        TextField lootTableField = new TextField();
        lootTableField.setPromptText("namespace:loot_table");

        Spinner<Integer> experienceSpinner = new Spinner<>();
        experienceSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, Integer.MAX_VALUE, 0));

        TextField commandField = new TextField();
        commandField.setPromptText("/say hello");
        CheckBox runAsServerBox = new CheckBox("Run as server");
        runAsServerBox.setSelected(true);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.add(new Label("Reward type:"), 0, 0);
        grid.add(typeBox, 1, 0);

        Label itemLabel = new Label("Item ID:");
        grid.add(itemLabel, 0, 1);
        grid.add(itemIdField, 1, 1);
        Label countLabel = new Label("Count:");
        grid.add(countLabel, 0, 2);
        grid.add(countSpinner, 1, 2);

        Label lootLabel = new Label("Loot table:");
        grid.add(lootLabel, 0, 3);
        grid.add(lootTableField, 1, 3);

        Label experienceLabel = new Label("Experience:");
        grid.add(experienceLabel, 0, 4);
        grid.add(experienceSpinner, 1, 4);

        Label commandLabel = new Label("Command:");
        grid.add(commandLabel, 0, 5);
        grid.add(commandField, 1, 5);
        grid.add(runAsServerBox, 1, 6);

        dialog.getDialogPane().setContent(grid);

        Node addButton = dialog.getDialogPane().lookupButton(addButtonType);

        Runnable updateFields = () -> {
            RewardType type = typeBox.getSelectionModel().getSelectedItem();
            if (type == null) {
                addButton.setDisable(true);
                return;
            }
            boolean itemSelected = type == RewardType.ITEM;
            boolean lootSelected = type == RewardType.LOOT_TABLE;
            boolean xpSelected = type == RewardType.EXPERIENCE;
            boolean commandSelected = type == RewardType.COMMAND;

            setRowVisibility(itemLabel, itemIdField, itemSelected);
            setRowVisibility(countLabel, countSpinner, itemSelected);
            setRowVisibility(lootLabel, lootTableField, lootSelected);
            setRowVisibility(experienceLabel, experienceSpinner, xpSelected);
            setRowVisibility(commandLabel, commandField, commandSelected);
            runAsServerBox.setManaged(commandSelected);
            runAsServerBox.setVisible(commandSelected);

            boolean valid = switch (type) {
                case ITEM -> !itemIdField.getText().trim().isEmpty();
                case LOOT_TABLE -> !lootTableField.getText().trim().isEmpty();
                case EXPERIENCE -> true;
                case COMMAND -> !commandField.getText().trim().isEmpty();
            };
            addButton.setDisable(!valid);
        };

        typeBox.valueProperty().addListener((obs, oldValue, newValue) -> updateFields.run());
        itemIdField.textProperty().addListener((obs, oldValue, newValue) -> updateFields.run());
        lootTableField.textProperty().addListener((obs, oldValue, newValue) -> updateFields.run());
        commandField.textProperty().addListener((obs, oldValue, newValue) -> updateFields.run());
        updateFields.run();

        configureDialogOwner(dialog);
        dialog.setResultConverter(button -> {
            if (button != addButtonType) {
                return null;
            }
            RewardType type = typeBox.getSelectionModel().getSelectedItem();
            if (type == null) {
                return null;
            }
            return switch (type) {
                case ITEM -> Reward.item(new ItemRef(itemIdField.getText().trim(), countSpinner.getValue()));
                case LOOT_TABLE -> Reward.lootTable(lootTableField.getText().trim());
                case EXPERIENCE -> Reward.experience(experienceSpinner.getValue());
                case COMMAND -> Reward.command(new RewardCommand(commandField.getText().trim(), runAsServerBox.isSelected()));
            };
        });
        dialog.showAndWait().ifPresent(viewModel.getRewards()::add);
    }

    private void setRowVisibility(Node label, Node field, boolean visible) {
        label.setManaged(visible);
        label.setVisible(visible);
        field.setManaged(visible);
        field.setVisible(visible);
    }

    private String formatReward(Reward reward) {
        return switch (reward.type()) {
            case ITEM -> reward.item()
                    .map(item -> "Item: " + item.itemId() + " x" + item.count())
                    .orElse("Item reward");
            case LOOT_TABLE -> "Loot table: " + reward.lootTableId().orElse("(unset)");
            case EXPERIENCE -> "Experience: " + reward.experience().orElse(0);
            case COMMAND -> reward.command()
                    .map(command -> "Command: " + command.command())
                    .orElse("Command reward");
        };
    }

    private void configureDialogOwner(Dialog<?> dialog) {
        if (rootPane != null && rootPane.getScene() != null) {
            Window owner = rootPane.getScene().getWindow();
            if (owner != null) {
                dialog.initOwner(owner);
                dialog.initModality(Modality.WINDOW_MODAL);
            }
        }
    }

    void openItemBrowser() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/dev/ftbq/editor/view/item_browser.fxml"));
            Parent root = loader.load();
            ItemBrowserController controller = loader.getController();
            controller.setOnItemSelected(entity -> {
                if (entity != null) {
                    IconRef selectedIcon = new IconRef(entity.id());
                    viewModel.iconProperty().set(selectedIcon);
                }
            });

            Stage stage = new Stage();
            stage.setTitle("Item Browser");
            stage.setScene(new Scene(root));
            ThemeService.getInstance().registerStage(stage);
            if (rootPane != null && rootPane.getScene() != null) {
                stage.initOwner(rootPane.getScene().getWindow());
                stage.initModality(Modality.WINDOW_MODAL);
            }
            stage.setOnHidden(event -> controller.dispose());
            stage.show();
        } catch (IOException e) {
            logger.error("Failed to open item browser", e);
        }
    }

    private void registerUndoStateListener() {
        if (undoStateListenerRegistered || commandBus == null) {
            return;
        }
        commandBus.subscribeAll(command -> Platform.runLater(this::updateUndoRedoButtons));
        undoStateListenerRegistered = true;
    }

    private void updateUndoRedoButtons() {
        boolean canUndo = undoManager != null && undoManager.canUndo();
        boolean canRedo = undoManager != null && undoManager.canRedo();
        if (undoButton != null) {
            undoButton.setDisable(!canUndo);
        }
        if (redoButton != null) {
            redoButton.setDisable(!canRedo);
        }
    }

    private boolean validateQuestConfiguration() {
        Quest quest = viewModel.toQuest();
        if (quest.title() == null || quest.title().isBlank()) {
            showValidationError("Quest title must not be empty.");
            return false;
        }
        if (quest.description() == null || quest.description().isBlank()) {
            showValidationError("Quest description must not be empty.");
            return false;
        }

        Set<String> dependencyIds = new HashSet<>();
        for (Dependency dependency : quest.dependencies()) {
            if (!dependencyIds.add(dependency.questId())) {
                showValidationError("Duplicate dependency found for quest: " + dependency.questId());
                return false;
            }
            if (quest.id() != null && quest.id().equals(dependency.questId())) {
                showValidationError("Quest cannot depend on itself: " + dependency.questId());
                return false;
            }
        }

        if (quest.id() != null && UiServiceLocator.storeDao != null) {
            for (Dependency dependency : quest.dependencies()) {
                String dependencyId = dependency.questId();
                try {
                    Optional<Quest> dependencyQuest = UiServiceLocator.storeDao.findQuestHeaderById(dependencyId);
                    if (dependencyQuest.isPresent() && isCircularDependency(quest.id(), dependencyQuest.get(), new HashSet<>())) {
                        showValidationError("Circular dependency detected between quests: " + quest.id() + " and " + dependencyId);
                        return false;
                    }
                } catch (Exception exception) {
                    logger.error("Failed to check circular dependency for quest " + quest.id() + " and dependency " + dependencyId, exception);
                }
            }
        }

        return true;
    }

    private boolean isCircularDependency(String rootQuestId, Quest currentQuest, Set<String> visitedQuestIds) {
        if (rootQuestId.equals(currentQuest.id())) {
            return true;
        }
        if (!visitedQuestIds.add(currentQuest.id())) {
            return false;
        }
        if (currentQuest.dependencies().isEmpty()) {
            return false;
        }
        for (Dependency dependency : currentQuest.dependencies()) {
            if (rootQuestId.equals(dependency.questId())) {
                return true;
            }
            Optional<Quest> nextQuest = UiServiceLocator.storeDao != null
                    ? UiServiceLocator.storeDao.findQuestHeaderById(dependency.questId())
                    : Optional.empty();
            if (nextQuest.isPresent() && isCircularDependency(rootQuestId, nextQuest.get(), visitedQuestIds)) {
                return true;
            }
        }
        return false;
    }

    private void showValidationError(String message) {
        logger.error(message, null);
        if (Platform.isFxApplicationThread()) {
            showAlert(message);
        } else {
            Platform.runLater(() -> showAlert(message));
        }
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Validation Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        if (rootPane != null && rootPane.getScene() != null) {
            alert.initOwner(rootPane.getScene().getWindow());
        }
        alert.showAndWait();
    }
}
