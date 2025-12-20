package dev.ftbq.editor.controller;

import dev.ftbq.editor.AppAware;
import dev.ftbq.editor.MainApp;
import dev.ftbq.editor.domain.AdvancementTask;
import dev.ftbq.editor.domain.BackgroundRef;
import dev.ftbq.editor.domain.BiomeTask;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.CheckmarkTask;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.DimensionTask;
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
import dev.ftbq.editor.domain.StructureTask;
import dev.ftbq.editor.domain.Task;
import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.domain.HexId;
import dev.ftbq.editor.ThemeService;
import dev.ftbq.editor.service.UserSettings;
import dev.ftbq.editor.store.Project;
import dev.ftbq.editor.ui.QuestNodeFactory;
import dev.ftbq.editor.view.graph.layout.QuestLayoutStore;
import dev.ftbq.editor.services.UiServiceLocator;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ChapterEditorController implements AppAware {
    private static final Logger LOGGER = Logger.getLogger(ChapterEditorController.class.getName());
    private static final PseudoClass SELECTED_PSEUDO_CLASS = PseudoClass.getPseudoClass("selected");
    private static final int DEFAULT_COLUMNS = 4;
    // Grid configuration
    private static final double GRID_SIZE = 50.0;
    private static final Color GRID_COLOR = Color.rgb(200, 200, 200, 0.5);
    private static final Color GRID_MAJOR_COLOR = Color.rgb(150, 150, 150, 0.7);
    private static final int MAJOR_GRID_INTERVAL = 5;

    @FXML private ComboBox<String> chapterGroupComboBox;
    @FXML private ListView<Chapter> chapterListView;
    @FXML private TextField chapterSearchField;
    @FXML private Label chapterTitle;
    @FXML private ScrollPane questScrollPane;
    @FXML private Pane gridCanvas;
    @FXML private Pane questPane;

    private Project project;
    private final List<Chapter> workingChapters = new ArrayList<>();
    private Chapter currentChapter;
    private Quest selectedQuest;
    private final Map<String, Node> questNodes = new HashMap<>();
    private final Map<String, Quest> nodeToQuest = new HashMap<>();
    private MainApp mainApp;
    private ContextMenu questContextMenu;
    private boolean chapterGroupComboBoxBound = false;
    private Point2D lastClickPosition = new Point2D(0, 0);
    private double dragStartX;
    private double dragStartY;
    private double dragOffsetX;
    private double dragOffsetY;
    private Node draggedNode;

    @Override
    public void setMainApp(MainApp app) {
        this.mainApp = app;
        bindChapterGroupComboBox();
    }

    public List<Chapter> getWorkingChapters() {
        return workingChapters;
    }

    private void bindChapterGroupComboBox() {
        if (chapterGroupComboBox == null || mainApp == null) {
            return;
        }
        ChapterGroupBrowserController groupBrowser = mainApp.getChapterGroupBrowserController();
        if (groupBrowser == null || groupBrowser.getViewModel() == null) {
            return;
        }
        var viewModel = groupBrowser.getViewModel();
        refreshChapterGroupComboBox(viewModel);
        if (!chapterGroupComboBoxBound) {
            chapterGroupComboBoxBound = true;
            viewModel.getChapterGroups().addListener((javafx.collections.ListChangeListener<? super dev.ftbq.editor.viewmodel.ChapterGroupBrowserViewModel.ChapterGroup>) change -> {
                refreshChapterGroupComboBox(viewModel);
            });
        }
    }

    private void refreshChapterGroupComboBox(dev.ftbq.editor.viewmodel.ChapterGroupBrowserViewModel viewModel) {
        if (chapterGroupComboBox == null || viewModel == null) {
            return;
        }
        String currentSelection = chapterGroupComboBox.getValue();
        List<String> groupNames = viewModel.getChapterGroups().stream()
                .map(g -> g.getName())
                .collect(Collectors.toList());
        chapterGroupComboBox.getItems().setAll(groupNames);
        if (currentSelection != null && groupNames.contains(currentSelection)) {
            chapterGroupComboBox.setValue(currentSelection);
        } else if (!groupNames.isEmpty()) {
            chapterGroupComboBox.getSelectionModel().selectFirst();
        }
    }

    @FXML
    public void initialize() {
        setupGridCanvas();
        setupContextMenu();
        setupQuestPane();

        if (questDetailsPanel != null) {
            questDetailsPanel.setVisible(false);
            questDetailsPanel.setManaged(false);
        }

        if (chapterListView != null) {
            chapterListView.setCellFactory(list -> new ListCell<>() {
                @Override
                protected void updateItem(Chapter item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.title());
                }
            });
            chapterListView.getSelectionModel().selectedItemProperty().addListener((obs, oldChapter, newChapter) -> {
                if (newChapter != null) {
                    displayChapter(newChapter);
                } else {
                    displayChapter(null);
                }
            });
        }

        if (chapterSearchField != null) {
            chapterSearchField.textProperty().addListener((obs, oldValue, newValue) -> refreshChapterList());
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

        setupTaskMenu();
        setupRewardMenu();
        clearQuestDetails();
        LOGGER.fine("ChapterEditorController initialized");
    }

    /**
     * Setup the grid canvas with gridlines.
     */
    private void setupGridCanvas() {
        if (gridCanvas == null) {
            return;
        }

        gridCanvas.widthProperty().addListener((obs, old, newVal) -> drawGrid());
        gridCanvas.heightProperty().addListener((obs, old, newVal) -> drawGrid());
        drawGrid();
    }

    /**
     * Draw the grid pattern on the canvas.
     */
    private void drawGrid() {
        if (gridCanvas == null) {
            return;
        }

        double width = Math.max(gridCanvas.getWidth(), gridCanvas.getMinWidth());
        double height = Math.max(gridCanvas.getHeight(), gridCanvas.getMinHeight());

        gridCanvas.getChildren().clear();

        for (int i = 0; i <= width / GRID_SIZE; i++) {
            double x = i * GRID_SIZE;
            Rectangle line = new Rectangle(x, 0, i % MAJOR_GRID_INTERVAL == 0 ? 2 : 1, height);
            line.setFill(i % MAJOR_GRID_INTERVAL == 0 ? GRID_MAJOR_COLOR : GRID_COLOR);
            line.setManaged(false);
            line.setMouseTransparent(true);
            gridCanvas.getChildren().add(line);
        }

        for (int i = 0; i <= height / GRID_SIZE; i++) {
            double y = i * GRID_SIZE;
            Rectangle line = new Rectangle(0, y, width, i % MAJOR_GRID_INTERVAL == 0 ? 2 : 1);
            line.setFill(i % MAJOR_GRID_INTERVAL == 0 ? GRID_MAJOR_COLOR : GRID_COLOR);
            line.setManaged(false);
            line.setMouseTransparent(true);
            gridCanvas.getChildren().add(line);
        }
    }

    /**
     * Setup the context menu for right-click on quest pane.
     */
    private void setupContextMenu() {
        questContextMenu = new ContextMenu();

        MenuItem createQuestItem = new MenuItem("Create Quest");
        createQuestItem.setOnAction(e -> {
            Point2D clickPoint = getLastClickPosition();
            createQuestAt(clickPoint.getX(), clickPoint.getY());
        });

        MenuItem pasteQuestItem = new MenuItem("Paste Quest");
        pasteQuestItem.setOnAction(e -> LOGGER.fine("Paste quest action triggered"));

        questContextMenu.getItems().addAll(createQuestItem, pasteQuestItem);
    }

    /**
     * Setup quest pane interactions.
     */
    private void setupQuestPane() {
        if (questPane == null) {
            return;
        }

        questPane.setOnContextMenuRequested(this::showContextMenu);
    }

    private void setupTaskMenu() {
        if (addTaskMenu == null) {
            return;
        }

        MenuItem checkmarkItem = new MenuItem("Checkmark");
        checkmarkItem.setOnAction(e -> addTaskToSelectedQuest(new CheckmarkTask()));

        MenuItem itemItem = new MenuItem("Item");
        itemItem.setOnAction(e -> showAddItemTaskDialog());

        MenuItem advancementItem = new MenuItem("Advancement");
        advancementItem.setOnAction(e -> showAddAdvancementTaskDialog());

        MenuItem locationItem = new MenuItem("Location");
        locationItem.setOnAction(e -> showAddLocationTaskDialog());

        MenuItem dimensionItem = new MenuItem("Dimension");
        dimensionItem.setOnAction(e -> showAddDimensionTaskDialog());

        MenuItem biomeItem = new MenuItem("Biome");
        biomeItem.setOnAction(e -> showAddBiomeTaskDialog());

        MenuItem structureItem = new MenuItem("Structure");
        structureItem.setOnAction(e -> showAddStructureTaskDialog());

        MenuItem killItem = new MenuItem("Kill");
        killItem.setOnAction(e -> showAddKillTaskDialog());

        MenuItem observationItem = new MenuItem("Observation");
        observationItem.setOnAction(e -> showAddObservationTaskDialog());

        MenuItem stageItem = new MenuItem("GameStage");
        stageItem.setOnAction(e -> showAddStageTaskDialog());

        addTaskMenu.getItems().addAll(checkmarkItem, itemItem, advancementItem, locationItem,
                dimensionItem, biomeItem, structureItem, killItem, observationItem, stageItem);
    }

    private void setupRewardMenu() {
        if (addRewardMenu == null) {
            return;
        }

        MenuItem itemRewardItem = new MenuItem("Item");
        itemRewardItem.setOnAction(e -> showAddItemRewardDialog());

        MenuItem xpRewardItem = new MenuItem("XP Amount");
        xpRewardItem.setOnAction(e -> showAddXpRewardDialog());

        MenuItem xpLevelsRewardItem = new MenuItem("XP Levels");
        xpLevelsRewardItem.setOnAction(e -> showAddXpLevelsRewardDialog());

        MenuItem commandRewardItem = new MenuItem("Command");
        commandRewardItem.setOnAction(e -> showAddCommandRewardDialog());

        MenuItem stageRewardItem = new MenuItem("GameStage");
        stageRewardItem.setOnAction(e -> showAddStageRewardDialog());

        addRewardMenu.getItems().addAll(itemRewardItem, xpRewardItem, xpLevelsRewardItem,
                commandRewardItem, stageRewardItem);
    }

    private void showAddItemTaskDialog() {
        TextInputDialog dialog = new TextInputDialog("minecraft:diamond");
        dialog.setTitle("Add Item Task");
        dialog.setHeaderText("Add an item task");
        dialog.setContentText("Item ID (e.g. minecraft:diamond):");
        initDialogOwner(dialog);

        dialog.showAndWait().ifPresent(itemId -> {
            if (!itemId.isBlank()) {
                addTaskToSelectedQuest(new ItemTask(itemId.trim(), 1));
            }
        });
    }

    private void showAddAdvancementTaskDialog() {
        TextInputDialog dialog = new TextInputDialog("minecraft:story/mine_stone");
        dialog.setTitle("Add Advancement Task");
        dialog.setHeaderText("Add an advancement task");
        dialog.setContentText("Advancement ID:");
        initDialogOwner(dialog);

        dialog.showAndWait().ifPresent(advancementId -> {
            if (!advancementId.isBlank()) {
                addTaskToSelectedQuest(new AdvancementTask(advancementId.trim()));
            }
        });
    }

    private void showAddLocationTaskDialog() {
        TextInputDialog dialog = new TextInputDialog("minecraft:overworld");
        dialog.setTitle("Add Location Task");
        dialog.setHeaderText("Add a location task");
        dialog.setContentText("Dimension (e.g. minecraft:overworld):");
        initDialogOwner(dialog);

        dialog.showAndWait().ifPresent(dimension -> {
            if (!dimension.isBlank()) {
                addTaskToSelectedQuest(new LocationTask(dimension.trim(), 0, 64, 0, 100));
            }
        });
    }

    private void showAddItemRewardDialog() {
        TextInputDialog dialog = new TextInputDialog("minecraft:diamond");
        dialog.setTitle("Add Item Reward");
        dialog.setHeaderText("Add an item reward");
        dialog.setContentText("Item ID (e.g. minecraft:diamond):");
        initDialogOwner(dialog);

        dialog.showAndWait().ifPresent(itemId -> {
            if (!itemId.isBlank()) {
                addItemRewardToSelectedQuest(new ItemReward(new ItemRef(itemId.trim(), 1)));
            }
        });
    }

    private void showAddDimensionTaskDialog() {
        TextInputDialog dialog = new TextInputDialog("minecraft:the_nether");
        dialog.setTitle("Add Dimension Task");
        dialog.setHeaderText("Add a dimension visit task");
        dialog.setContentText("Dimension ID:");
        initDialogOwner(dialog);

        dialog.showAndWait().ifPresent(dimension -> {
            if (!dimension.isBlank()) {
                addTaskToSelectedQuest(new DimensionTask(dimension.trim()));
            }
        });
    }

    private void showAddBiomeTaskDialog() {
        TextInputDialog dialog = new TextInputDialog("minecraft:plains");
        dialog.setTitle("Add Biome Task");
        dialog.setHeaderText("Add a biome visit task");
        dialog.setContentText("Biome ID (or #tag):");
        initDialogOwner(dialog);

        dialog.showAndWait().ifPresent(biome -> {
            if (!biome.isBlank()) {
                addTaskToSelectedQuest(new BiomeTask(biome.trim()));
            }
        });
    }

    private void showAddStructureTaskDialog() {
        TextInputDialog dialog = new TextInputDialog("minecraft:mineshaft");
        dialog.setTitle("Add Structure Task");
        dialog.setHeaderText("Add a structure discovery task");
        dialog.setContentText("Structure ID (or #tag):");
        initDialogOwner(dialog);

        dialog.showAndWait().ifPresent(structure -> {
            if (!structure.isBlank()) {
                addTaskToSelectedQuest(new StructureTask(structure.trim()));
            }
        });
    }

    private void showAddKillTaskDialog() {
        TextInputDialog dialog = new TextInputDialog("minecraft:zombie");
        dialog.setTitle("Add Kill Task");
        dialog.setHeaderText("Add a kill task");
        dialog.setContentText("Entity type ID:");
        initDialogOwner(dialog);

        dialog.showAndWait().ifPresent(entityType -> {
            if (!entityType.isBlank()) {
                addTaskToSelectedQuest(new KillTask(entityType.trim(), 1));
            }
        });
    }

    private void showAddObservationTaskDialog() {
        TextInputDialog dialog = new TextInputDialog("minecraft:dirt");
        dialog.setTitle("Add Observation Task");
        dialog.setHeaderText("Add an observation task");
        dialog.setContentText("Block/Entity ID to observe:");
        initDialogOwner(dialog);

        dialog.showAndWait().ifPresent(target -> {
            if (!target.isBlank()) {
                addTaskToSelectedQuest(new ObservationTask(ObservationTask.ObserveType.BLOCK, target.trim()));
            }
        });
    }

    private void showAddStageTaskDialog() {
        TextInputDialog dialog = new TextInputDialog("my_stage");
        dialog.setTitle("Add GameStage Task");
        dialog.setHeaderText("Add a GameStage requirement task");
        dialog.setContentText("Stage name:");
        initDialogOwner(dialog);

        dialog.showAndWait().ifPresent(stage -> {
            if (!stage.isBlank()) {
                addTaskToSelectedQuest(new StageTask(stage.trim()));
            }
        });
    }

    private void showAddXpRewardDialog() {
        TextInputDialog dialog = new TextInputDialog("100");
        dialog.setTitle("Add XP Reward");
        dialog.setHeaderText("Add an XP amount reward");
        dialog.setContentText("XP amount:");
        initDialogOwner(dialog);

        dialog.showAndWait().ifPresent(xp -> {
            try {
                int amount = Integer.parseInt(xp.trim());
                if (amount > 0) {
                    updateQuestXpReward(amount, null);
                }
            } catch (NumberFormatException ignored) {
            }
        });
    }

    private void showAddXpLevelsRewardDialog() {
        TextInputDialog dialog = new TextInputDialog("5");
        dialog.setTitle("Add XP Levels Reward");
        dialog.setHeaderText("Add an XP levels reward");
        dialog.setContentText("XP levels:");
        initDialogOwner(dialog);

        dialog.showAndWait().ifPresent(xp -> {
            try {
                int levels = Integer.parseInt(xp.trim());
                if (levels > 0) {
                    updateQuestXpReward(null, levels);
                }
            } catch (NumberFormatException ignored) {
            }
        });
    }

    private void showAddCommandRewardDialog() {
        TextInputDialog dialog = new TextInputDialog("/give @p diamond 1");
        dialog.setTitle("Add Command Reward");
        dialog.setHeaderText("Add a command reward");
        dialog.setContentText("Command:");
        initDialogOwner(dialog);

        dialog.showAndWait().ifPresent(command -> {
            if (!command.isBlank()) {
                updateQuestCommandReward(command.trim());
            }
        });
    }

    private void showAddStageRewardDialog() {
        TextInputDialog dialog = new TextInputDialog("my_stage");
        dialog.setTitle("Add GameStage Reward");
        dialog.setHeaderText("Add a GameStage reward");
        dialog.setContentText("Stage name:");
        initDialogOwner(dialog);

        dialog.showAndWait().ifPresent(stage -> {
            if (!stage.isBlank()) {
                LOGGER.info("GameStage reward added: " + stage.trim());
            }
        });
    }

    private void updateQuestXpReward(Integer xpAmount, Integer xpLevels) {
        if (selectedQuest == null || currentChapter == null) {
            return;
        }
        Quest updatedQuest = Quest.builder()
                .id(selectedQuest.id())
                .title(selectedQuest.title())
                .description(selectedQuest.description())
                .icon(selectedQuest.icon())
                .visibility(selectedQuest.visibility())
                .tasks(selectedQuest.tasks())
                .itemRewards(selectedQuest.itemRewards())
                .experienceAmount(xpAmount)
                .experienceLevels(xpLevels)
                .lootTableId(selectedQuest.lootTableId())
                .commandReward(selectedQuest.commandReward())
                .dependencies(selectedQuest.dependencies())
                .build();

        updateQuestInChapter(selectedQuest, updatedQuest);
    }

    private void updateQuestCommandReward(String command) {
        if (selectedQuest == null || currentChapter == null) {
            return;
        }
        Quest updatedQuest = Quest.builder()
                .id(selectedQuest.id())
                .title(selectedQuest.title())
                .description(selectedQuest.description())
                .icon(selectedQuest.icon())
                .visibility(selectedQuest.visibility())
                .tasks(selectedQuest.tasks())
                .itemRewards(selectedQuest.itemRewards())
                .experienceAmount(selectedQuest.experienceAmount())
                .experienceLevels(selectedQuest.experienceLevels())
                .lootTableId(selectedQuest.lootTableId())
                .commandReward(new RewardCommand(command, true))
                .dependencies(selectedQuest.dependencies())
                .build();

        updateQuestInChapter(selectedQuest, updatedQuest);
    }

    private void initDialogOwner(TextInputDialog dialog) {
        Window owner = questPane != null && questPane.getScene() != null
                ? questPane.getScene().getWindow() : null;
        if (owner != null) {
            dialog.initOwner(owner);
        }
    }

    private void addTaskToSelectedQuest(Task task) {
        if (selectedQuest == null || currentChapter == null) {
            return;
        }
        List<Task> updatedTasks = new ArrayList<>(selectedQuest.tasks());
        updatedTasks.add(task);

        Quest updatedQuest = Quest.builder()
                .id(selectedQuest.id())
                .title(selectedQuest.title())
                .description(selectedQuest.description())
                .icon(selectedQuest.icon())
                .visibility(selectedQuest.visibility())
                .tasks(updatedTasks)
                .itemRewards(selectedQuest.itemRewards())
                .experienceAmount(selectedQuest.experienceAmount())
                .experienceLevels(selectedQuest.experienceLevels())
                .lootTableId(selectedQuest.lootTableId())
                .commandReward(selectedQuest.commandReward())
                .dependencies(selectedQuest.dependencies())
                .build();

        updateQuestInChapter(selectedQuest, updatedQuest);
    }

    private void addItemRewardToSelectedQuest(ItemReward reward) {
        if (selectedQuest == null || currentChapter == null) {
            return;
        }
        List<ItemReward> updatedRewards = new ArrayList<>(selectedQuest.itemRewards());
        updatedRewards.add(reward);

        Quest updatedQuest = Quest.builder()
                .id(selectedQuest.id())
                .title(selectedQuest.title())
                .description(selectedQuest.description())
                .icon(selectedQuest.icon())
                .visibility(selectedQuest.visibility())
                .tasks(selectedQuest.tasks())
                .itemRewards(updatedRewards)
                .experienceAmount(selectedQuest.experienceAmount())
                .experienceLevels(selectedQuest.experienceLevels())
                .lootTableId(selectedQuest.lootTableId())
                .commandReward(selectedQuest.commandReward())
                .dependencies(selectedQuest.dependencies())
                .build();

        updateQuestInChapter(selectedQuest, updatedQuest);
    }

    /**
     * Handle mouse press on quest pane (for right-click context menu).
     */
    @FXML
    private void onQuestGridMousePressed(MouseEvent event) {
        lastClickPosition = new Point2D(event.getX(), event.getY());

        if (event.getButton() == MouseButton.SECONDARY) {
            event.consume();
        } else if (questContextMenu != null) {
            questContextMenu.hide();
        }
    }

    /**
     * Handle mouse click on quest pane.
     */
    @FXML
    private void onQuestPaneClicked(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
            if (event.getTarget() == questPane) {
                deselectQuest();
            }
        }
    }

    /**
     * Show context menu at click position.
     */
    private void showContextMenu(ContextMenuEvent event) {
        if (questContextMenu != null && questPane != null) {
            questContextMenu.show(questPane, event.getScreenX(), event.getScreenY());
        }
        event.consume();
    }

    /**
     * Get the last click position (for context menu actions).
     */
    private Point2D getLastClickPosition() {
        return lastClickPosition;
    }

    /**
     * Create a new quest at the specified position.
     */
    private void createQuestAt(double x, double y) {
        double snappedX = Math.round(x / GRID_SIZE) * GRID_SIZE;
        double snappedY = Math.round(y / GRID_SIZE) * GRID_SIZE;
        Quest quest = createNewQuest(new Point2D(snappedX, snappedY));
        if (quest != null) {
            Node node = questNodes.get(quest.id());
            if (node != null) {
                node.relocate(snappedX, snappedY);
            }
            selectQuest(quest);
        }
        if (questContextMenu != null) {
            questContextMenu.hide();
        }
    }

    /**
     * Create a visual quest node placeholder.
     */
    private Rectangle createQuestNode(double x, double y) {
        Rectangle node = new Rectangle(x, y, GRID_SIZE * 2, GRID_SIZE * 2);
        node.setFill(Color.rgb(100, 150, 255, 0.8));
        node.setStroke(Color.rgb(50, 100, 200));
        node.setStrokeWidth(2);
        node.setArcWidth(10);
        node.setArcHeight(10);
        node.setManaged(false);

        setupQuestNodeDragging(node);

        node.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                if (e.getClickCount() == 2) {
                    LOGGER.fine("Double-clicked quest placeholder");
                } else {
                    selectQuest(node);
                }
                e.consume();
            }
        });

        return node;
    }

    /**
     * Setup dragging for quest nodes.
     */
    private void setupQuestNodeDragging(Rectangle node) {
        final double[] dragDelta = new double[2];

        node.setOnMousePressed(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                dragDelta[0] = node.getX() - e.getX();
                dragDelta[1] = node.getY() - e.getY();
            }
        });

        node.setOnMouseDragged(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                double newX = e.getX() + dragDelta[0];
                double newY = e.getY() + dragDelta[1];
                node.setX(Math.round(newX / GRID_SIZE) * GRID_SIZE);
                node.setY(Math.round(newY / GRID_SIZE) * GRID_SIZE);
            }
        });
    }

    /**
     * Select a quest node placeholder and show details panel.
     */
    private void selectQuest(Rectangle node) {
        if (questPane == null) {
            return;
        }

        questPane.getChildren().forEach(child -> {
            if (child instanceof Rectangle rect) {
                rect.setStroke(Color.rgb(50, 100, 200));
                rect.setStrokeWidth(2);
            }
        });

        node.setStroke(Color.YELLOW);
        node.setStrokeWidth(3);

        if (questDetailsPanel != null) {
            questDetailsPanel.setVisible(true);
            questDetailsPanel.setManaged(true);
        }
    }

    /**
     * Deselect quest and hide details panel.
     */
    private void deselectQuest() {
        questNodes.values().forEach(node -> node.pseudoClassStateChanged(SELECTED_PSEUDO_CLASS, false));
        if (questPane != null) {
            questPane.getChildren().forEach(child -> {
                if (child instanceof Rectangle rect) {
                    rect.setStroke(Color.rgb(50, 100, 200));
                    rect.setStrokeWidth(2);
                }
            });
        }
        selectedQuest = null;
        clearQuestDetails();
    }

    public void setProject(Project project) {
        this.project = project;
        workingChapters.clear();
        bindChapterGroupComboBox();
        if (project != null) {
            workingChapters.addAll(project.getChapters());
            refreshChapterList();
        } else {
            if (chapterListView != null) {
                chapterListView.getItems().clear();
            }
            displayChapter(null);
        }
    }

    public void focusChapter(String chapterId) {
        if (chapterId == null || workingChapters.isEmpty() || chapterListView == null) {
            return;
        }
        Optional<Chapter> target = workingChapters.stream()
                .filter(chapter -> chapterId.equals(chapter.id()))
                .findFirst();
        if (target.isEmpty()) {
            return;
        }

        Chapter chapter = target.get();
        if (chapterSearchField != null) {
            String query = chapterSearchField.getText();
            if (query != null && !query.isBlank() && !chapterListView.getItems().contains(chapter)) {
                chapterSearchField.setText("");
            }
        }

        if (!chapterListView.getItems().contains(chapter)) {
            refreshChapterList();
        }

        chapterListView.getSelectionModel().select(chapter);
        chapterListView.scrollTo(chapter);
        displayChapter(chapter);
    }

    private void refreshChapterList() {
        if (chapterListView == null) {
            return;
        }
        if (workingChapters.isEmpty()) {
            chapterListView.getItems().clear();
            displayChapter(null);
            return;
        }

        List<Chapter> chapters = new ArrayList<>(workingChapters);
        List<Chapter> filtered = chapters;
        if (chapterSearchField != null) {
            String query = chapterSearchField.getText();
            if (query != null && !query.isBlank()) {
                String filter = query.toLowerCase(Locale.ROOT);
                filtered = chapters.stream()
                        .filter(chapter -> chapter.title().toLowerCase(Locale.ROOT).contains(filter))
                        .collect(Collectors.toList());
            }
        }

        ObservableList<Chapter> items = chapterListView.getItems();
        items.setAll(filtered);
        if (!filtered.isEmpty()) {
            Chapter selection = chapterListView.getSelectionModel().getSelectedItem();
            if (selection == null || !filtered.contains(selection)) {
                chapterListView.getSelectionModel().selectFirst();
            } else {
                displayChapter(selection);
            }
        } else {
            displayChapter(null);
        }
    }

    @FXML
    private void onChapterGroupSelected() {
        if (chapterGroupComboBox == null || project == null || chapterListView == null) {
            return;
        }
        String selectedGroupName = chapterGroupComboBox.getValue();
        if (selectedGroupName == null || selectedGroupName.isBlank()) {
            chapterListView.setItems(FXCollections.observableArrayList(workingChapters));
            return;
        }
        
        ChapterGroupBrowserController groupBrowser = mainApp != null ? mainApp.getChapterGroupBrowserController() : null;
        if (groupBrowser == null || groupBrowser.getViewModel() == null) {
            chapterListView.setItems(FXCollections.observableArrayList(workingChapters));
            return;
        }
        
        var viewModel = groupBrowser.getViewModel();
        var selectedGroup = viewModel.getChapterGroups().stream()
                .filter(g -> selectedGroupName.equals(g.getName()))
                .findFirst()
                .orElse(null);
        
        if (selectedGroup == null) {
            chapterListView.setItems(FXCollections.observableArrayList(workingChapters));
            return;
        }
        
        var chapterIdsInGroup = selectedGroup.getChapters().stream()
                .map(c -> c.getId())
                .collect(Collectors.toSet());
        
        List<Chapter> filteredChapters = workingChapters.stream()
                .filter(chapter -> chapterIdsInGroup.contains(chapter.id()))
                .collect(Collectors.toList());
        
        chapterListView.setItems(FXCollections.observableArrayList(filteredChapters));
        if (!filteredChapters.isEmpty()) {
            chapterListView.getSelectionModel().selectFirst();
        } else {
            displayChapter(null);
        }
    }

    @FXML
    private void onCreateChapterGroup() {
        if (mainApp == null) {
            LOGGER.warning("MainApp not set; cannot create chapter group");
            return;
        }
        ChapterGroupBrowserController groupBrowser = mainApp.getChapterGroupBrowserController();
        if (groupBrowser != null) {
            groupBrowser.promptAddGroup();
        } else {
            LOGGER.warning("ChapterGroupBrowserController not available");
        }
    }

    @FXML
    private void onChapterSelected() {
        if (chapterListView == null) {
            return;
        }
        Chapter selected = chapterListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            displayChapter(selected);
        }
    }

    private void displayChapter(Chapter chapter) {
        currentChapter = chapter;
        if (chapterTitle != null) {
            chapterTitle.setText(chapter != null ? chapter.title() : "");
        }

        questNodes.clear();
        nodeToQuest.clear();
        if (questPane != null) {
            questPane.getChildren().clear();
        }
        deselectQuest();

        if (chapter == null || questPane == null) {
            return;
        }

        List<Quest> quests = chapter.quests();
        for (int i = 0; i < quests.size(); i++) {
            Quest quest = quests.get(i);
            Node node = QuestNodeFactory.create(quest, this::openQuestEditor);
            Point2D coordinates = resolveQuestCoordinates(chapter, quest, i);
            node.relocate(coordinates.getX(), coordinates.getY());
            node.setManaged(false);
            questPane.getChildren().add(node);
            questNodes.put(quest.id(), node);
            nodeToQuest.put(quest.id(), quest);
            node.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    selectQuest(quest);
                    event.consume();
                }
            });
            setupDragHandlers(node, quest);
        }

        drawDependencyLines(quests);

        if (!quests.isEmpty()) {
            selectQuest(quests.get(0));
        }
    }

    private void drawDependencyLines(List<Quest> quests) {
        if (questPane == null) {
            return;
        }

        for (Quest quest : quests) {
            Node questNode = questNodes.get(quest.id());
            if (questNode == null) {
                continue;
            }

            for (Dependency dependency : quest.dependencies()) {
                Node depNode = questNodes.get(dependency.questId());
                if (depNode == null) {
                    continue;
                }

                double startX = depNode.getLayoutX() + depNode.getBoundsInLocal().getWidth() / 2;
                double startY = depNode.getLayoutY() + depNode.getBoundsInLocal().getHeight() / 2;
                double endX = questNode.getLayoutX() + questNode.getBoundsInLocal().getWidth() / 2;
                double endY = questNode.getLayoutY() + questNode.getBoundsInLocal().getHeight() / 2;

                Line line = new Line(startX, startY, endX, endY);
                line.setStroke(dependency.required() ? Color.DARKGRAY : Color.LIGHTGRAY);
                line.setStrokeWidth(dependency.required() ? 2.0 : 1.5);
                line.getStrokeDashArray().addAll(dependency.required() ? List.of() : List.of(5.0, 5.0));
                line.setMouseTransparent(true);

                questPane.getChildren().add(0, line);
            }
        }
    }

    private void setupDragHandlers(Node node, Quest quest) {
        node.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                draggedNode = node;
                dragStartX = node.getLayoutX();
                dragStartY = node.getLayoutY();
                dragOffsetX = event.getSceneX() - node.getLayoutX();
                dragOffsetY = event.getSceneY() - node.getLayoutY();
                node.toFront();
                node.setCursor(javafx.scene.Cursor.CLOSED_HAND);
            }
        });

        node.setOnMouseDragged(event -> {
            if (draggedNode == node && event.getButton() == MouseButton.PRIMARY) {
                double newX = event.getSceneX() - dragOffsetX;
                double newY = event.getSceneY() - dragOffsetY;
                newX = Math.round(newX / GRID_SIZE) * GRID_SIZE;
                newY = Math.round(newY / GRID_SIZE) * GRID_SIZE;
                newX = Math.max(0, newX);
                newY = Math.max(0, newY);
                node.relocate(newX, newY);
                redrawDependencyLines();
            }
        });

        node.setOnMouseReleased(event -> {
            if (draggedNode == node) {
                node.setCursor(javafx.scene.Cursor.HAND);
                double finalX = node.getLayoutX();
                double finalY = node.getLayoutY();
                if (finalX != dragStartX || finalY != dragStartY) {
                    saveQuestPosition(quest, finalX, finalY);
                }
                draggedNode = null;
            }
        });

        node.setOnMouseEntered(event -> {
            if (draggedNode == null) {
                node.setCursor(javafx.scene.Cursor.HAND);
            }
        });

        node.setOnMouseExited(event -> {
            if (draggedNode == null) {
                node.setCursor(javafx.scene.Cursor.DEFAULT);
            }
        });
    }

    private void redrawDependencyLines() {
        if (questPane == null || currentChapter == null) {
            return;
        }
        questPane.getChildren().removeIf(node -> node instanceof Line);
        drawDependencyLines(currentChapter.quests());
    }

    private void saveQuestPosition(Quest quest, double x, double y) {
        if (currentChapter == null) {
            return;
        }
        QuestLayoutStore layoutStore = UiServiceLocator.questLayoutStore;
        if (layoutStore != null) {
            try {
                layoutStore.putNodePos(currentChapter.id(), quest.id(), x, y);
                LOGGER.fine("Saved position for quest " + quest.id() + ": (" + x + ", " + y + ")");
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING, "Failed to save quest position", e);
            }
        }
    }

    private Point2D resolveQuestCoordinates(Chapter chapter, Quest quest, int index) {
        QuestLayoutStore layoutStore = UiServiceLocator.questLayoutStore;
        if (layoutStore != null) {
            try {
                Optional<Point2D> pos = layoutStore.getNodePos(chapter.id(), quest.id());
                if (pos.isPresent()) {
                    Point2D point = pos.get();
                    double x = Math.max(0, point.getX());
                    double y = Math.max(0, point.getY());
                    return new Point2D(x, y);
                }
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "Unable to resolve quest coordinates for " + quest.id(), ex);
            }
        }
        int column = index % DEFAULT_COLUMNS;
        int row = index / DEFAULT_COLUMNS;
        double x = column * GRID_SIZE * 4;
        double y = row * GRID_SIZE * 4;
        return new Point2D(x, y);
    }

    private void selectQuest(Quest quest) {
        if (quest == null) {
            deselectQuest();
            return;
        }

        selectedQuest = quest;
        questNodes.forEach((questId, node) ->
                node.pseudoClassStateChanged(SELECTED_PSEUDO_CLASS, questId.equals(quest.id())));
        updateQuestDetails(quest);
    }

    private void updateQuestDetails(Quest quest) {
    }

    private void clearQuestDetails() {
    }

    private String describeTask(Task task) {
        return task != null ? task.describe() : "Unknown task";
    }

    private String describeDependency(Dependency dependency) {
        if (dependency == null) {
            return "Unknown dependency";
        }
        String suffix = dependency.required() ? "required" : "optional";
        return dependency.questId() + " (" + suffix + ")";
    }

    private void openQuestEditor(Quest quest) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/dev/ftbq/editor/view/quest_editor.fxml"));
            Parent root = loader.load();
            QuestEditorController controller = loader.getController();
            controller.setQuest(quest);

            Stage stage = new Stage();
            stage.setTitle("Quest Editor - " + quest.title());
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            Window owner = questPane != null && questPane.getScene() != null
                    ? questPane.getScene().getWindow()
                    : null;
            if (owner != null) {
                stage.initOwner(owner);
            }
            stage.showAndWait();

            if (controller.isSaved()) {
                Quest updatedQuest = controller.getUpdatedQuest();
                if (updatedQuest != null) {
                    updateQuestInChapter(quest, updatedQuest);
                }
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to open quest editor for quest " + quest.id(), e);
        }
    }

    private void updateQuestInChapter(Quest oldQuest, Quest newQuest) {
        if (currentChapter == null) {
            return;
        }
        List<Quest> updatedQuests = new ArrayList<>();
        for (Quest q : currentChapter.quests()) {
            if (q.id().equals(oldQuest.id())) {
                updatedQuests.add(newQuest);
            } else {
                updatedQuests.add(q);
            }
        }
        Chapter previousChapter = currentChapter;
        Chapter updatedChapter = new Chapter(
                previousChapter.id(),
                previousChapter.title(),
                previousChapter.icon(),
                previousChapter.background(),
                updatedQuests,
                previousChapter.visibility()
        );
        currentChapter = updatedChapter;
        updateChapterList(previousChapter, updatedChapter);
        replaceWorkingChapter(previousChapter, updatedChapter);
        displayChapter(updatedChapter);
        selectQuest(newQuest);
    }

    private Quest createNewQuest() {
        return createNewQuest(null);
    }

    private Quest createNewQuest(Point2D initialPosition) {
        if (currentChapter == null) {
            return null;
        }

        Quest newQuest = Quest.builder()
                .id(generateHexId())
                .title("New Quest")
                .description("")
                .build();

        Chapter previousChapter = currentChapter;
        List<Quest> updatedQuests = new ArrayList<>(previousChapter.quests());
        updatedQuests.add(newQuest);
        Chapter updatedChapter = new Chapter(previousChapter.id(), previousChapter.title(), previousChapter.icon(),
                previousChapter.background(), updatedQuests, previousChapter.visibility());
        currentChapter = updatedChapter;
        updateChapterList(previousChapter, updatedChapter);
        replaceWorkingChapter(previousChapter, updatedChapter);

        if (initialPosition != null) {
            QuestLayoutStore layoutStore = UiServiceLocator.questLayoutStore;
            if (layoutStore != null) {
                try {
                    String chapterId = updatedChapter.id();
                    if (chapterId != null && !chapterId.isBlank()) {
                        layoutStore.putNodePos(chapterId, newQuest.id(), initialPosition.getX(), initialPosition.getY());
                    }
                } catch (RuntimeException e) {
                    LOGGER.log(Level.FINE, "Unable to persist quest position for " + newQuest.id(), e);
                }
            }
        }

        displayChapter(updatedChapter);
        notifyChapterGroupBrowserUpdate();
        return newQuest;
    }

    private void notifyChapterGroupBrowserUpdate() {
        if (mainApp == null) {
            return;
        }
        ChapterGroupBrowserController groupBrowser = mainApp.getChapterGroupBrowserController();
        if (groupBrowser != null) {
            groupBrowser.updateTree();
        }
    }

    private void updateChapterList(Chapter previousChapter, Chapter updatedChapter) {
        if (chapterListView == null) {
            return;
        }

        ObservableList<Chapter> items = chapterListView.getItems();
        for (int i = 0; i < items.size(); i++) {
            Chapter item = items.get(i);
            if (item.id().equals(previousChapter.id())) {
                items.set(i, updatedChapter);
                chapterListView.getSelectionModel().select(updatedChapter);
                return;
            }
        }
    }

    private void replaceWorkingChapter(Chapter previousChapter, Chapter updatedChapter) {
        for (int i = 0; i < workingChapters.size(); i++) {
            Chapter item = workingChapters.get(i);
            if (item.id().equals(previousChapter.id())) {
                workingChapters.set(i, updatedChapter);
                return;
            }
        }
        workingChapters.add(updatedChapter);
    }

    @FXML
    private void onAddDependency() {
        LOGGER.log(Level.FINE, "onAddDependency invoked");
    }

    @FXML
    private void onRemoveQuest() {
        LOGGER.log(Level.FINE, "onRemoveQuest invoked");
    }

    @FXML
    private void onCreateQuest() {
        if (currentChapter == null) {
            LOGGER.warning("Cannot create quest: no chapter selected");
            return;
        }
        Quest newQuest = createNewQuest();
        if (newQuest != null) {
            openQuestEditor(newQuest);
        }
    }

    @FXML
    private void onCreateChapter() {
        showCreateChapterDialog();
    }

    private void showCreateChapterDialog() {
        TextInputDialog dialog = new TextInputDialog("New Chapter");
        dialog.setTitle("Create Chapter");
        dialog.setHeaderText("Create a new chapter");
        dialog.setContentText("Chapter title:");

        Window owner = questPane != null && questPane.getScene() != null
                ? questPane.getScene().getWindow()
                : null;
        if (owner != null) {
            dialog.initOwner(owner);
        }

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(title -> {
            if (title.isBlank()) {
                return;
            }
            String chapterId = generateHexId();
            Chapter newChapter = Chapter.builder()
                    .id(chapterId)
                    .title(title)
                    .icon(new IconRef("minecraft:book"))
                    .background(new BackgroundRef("minecraft:textures/gui/advancements/backgrounds/stone.png"))
                    .visibility(Visibility.VISIBLE)
                    .quests(List.of())
                    .build();

            workingChapters.add(newChapter);
            if (chapterListView != null) {
                chapterListView.getItems().add(newChapter);
                chapterListView.getSelectionModel().select(newChapter);
            }
            displayChapter(newChapter);
            
            addChapterToViewModel(chapterId, title);
            
            LOGGER.info("Created new chapter: " + title + " with ID " + chapterId);
        });
    }

    private String generateHexId() {
        return HexId.generate();
    }

    private void addChapterToViewModel(String chapterId, String chapterName) {
        if (mainApp == null) {
            return;
        }
        ChapterGroupBrowserController groupBrowser = mainApp.getChapterGroupBrowserController();
        if (groupBrowser == null || groupBrowser.getViewModel() == null) {
            return;
        }
        var viewModel = groupBrowser.getViewModel();
        
        String selectedGroupName = chapterGroupComboBox != null ? chapterGroupComboBox.getValue() : null;
        dev.ftbq.editor.viewmodel.ChapterGroupBrowserViewModel.ChapterGroup targetGroup = null;
        
        if (selectedGroupName != null && !selectedGroupName.isBlank()) {
            targetGroup = viewModel.getChapterGroups().stream()
                    .filter(g -> selectedGroupName.equals(g.getName()))
                    .findFirst()
                    .orElse(null);
        }
        
        if (targetGroup == null) {
            targetGroup = viewModel.getChapterGroups().stream()
                    .filter(g -> "Ungrouped Chapters".equals(g.getName()))
                    .findFirst()
                    .orElse(null);
            if (targetGroup == null && !viewModel.getChapterGroups().isEmpty()) {
                targetGroup = viewModel.getChapterGroups().get(0);
            }
        }
        
        if (targetGroup != null) {
            viewModel.addChapter(targetGroup, chapterId, chapterName);
        }
        notifyChapterGroupBrowserUpdate();
    }

    @FXML
    private void openSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/dev/ftbq/editor/view/settings.fxml"));
            Parent root = loader.load();
            SettingsController controller = loader.getController();

            Window owner = questPane != null && questPane.getScene() != null ? questPane.getScene().getWindow() : null;
            Scene mainScene = owner != null ? owner.getScene() : null;
            if (controller != null && mainScene != null) {
                controller.setScene(mainScene);
            }

            Stage dialog = new Stage();
            dialog.setTitle("Settings");
            dialog.initModality(Modality.APPLICATION_MODAL);
            if (owner != null) {
                dialog.initOwner(owner);
            }

            Scene dialogScene = new Scene(root);
            ThemeService.apply(dialogScene, UserSettings.get().darkTheme);

            CheckBox showGrid = (CheckBox) root.lookup("#chkShowGrid");
            if (showGrid != null) {
                showGrid.selectedProperty().addListener((obs, oldVal, newVal) -> {
                    // retained for settings compatibility
                });
            }
            CheckBox smooth = (CheckBox) root.lookup("#chkSmoothPanning");
            if (smooth != null) {
                smooth.selectedProperty().addListener((obs, oldVal, newVal) -> {
                    // retained for settings compatibility
                });
            }
            CheckBox dark = (CheckBox) root.lookup("#chkDarkTheme");
            if (dark != null) {
                dark.selectedProperty().addListener((obs, oldVal, newVal) -> ThemeService.apply(dialogScene, Boolean.TRUE.equals(newVal)));
            }

            dialog.setScene(dialogScene);
            dialog.showAndWait();

            var es = UserSettings.get();
            if (dialogScene != null) {
                ThemeService.apply(dialogScene, es.darkTheme);
            }
        } catch (Exception ignored) {
        }
    }
}
