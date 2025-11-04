package dev.ftbq.editor.controller;

import dev.ftbq.editor.AppAware;
import dev.ftbq.editor.MainApp;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.ChapterGroup;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.ItemReward;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.Task;
import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.service.ThemeService;
import dev.ftbq.editor.service.UserSettings;
import dev.ftbq.editor.store.Project;
import dev.ftbq.editor.domain.QuestFile;
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
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ComboBox;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
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

    @FXML private ComboBox<ChapterGroup> chapterGroupComboBox;
    @FXML private ListView<Chapter> chapterListView;
    @FXML private TextField chapterSearchField;
    @FXML private Label chapterTitle;
    @FXML private ScrollPane questScrollPane;
    @FXML private Pane gridCanvas;
    @FXML private Pane questPane;
    @FXML private VBox questDetailsPanel;
    @FXML private ListView<String> taskList;
    @FXML private ListView<String> rewardList;
    @FXML private ListView<String> dependencyList;
    @FXML private MenuButton addTaskMenu;
    @FXML private MenuButton addRewardMenu;

    private Project project;
    private final List<Chapter> workingChapters = new ArrayList<>();
    private final ObservableList<ChapterGroup> chapterGroups = FXCollections.observableArrayList();
    private final Map<String, Chapter> chapterById = new HashMap<>();
    private Chapter currentChapter;
    private Quest selectedQuest;
    private final Map<String, Node> questNodes = new HashMap<>();
    private MainApp mainApp;
    private ContextMenu questContextMenu;
    private Point2D lastClickPosition = new Point2D(0, 0);
    private static final String SYNTHETIC_UNGROUPED_ID = "__ungrouped__";

    @Override
    public void setMainApp(MainApp app) {
        this.mainApp = app;
    }

    @FXML
    public void initialize() {
        setupGridCanvas();
        setupContextMenu();
        setupQuestPane();

        if (chapterGroupComboBox != null) {
            chapterGroupComboBox.setItems(chapterGroups);
            chapterGroupComboBox.setCellFactory(list -> new ListCell<>() {
                @Override
                protected void updateItem(ChapterGroup item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.title());
                }
            });
            chapterGroupComboBox.setButtonCell(new ListCell<>() {
                @Override
                protected void updateItem(ChapterGroup item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? "Select group..." : item.title());
                }
            });
            chapterGroupComboBox.valueProperty().addListener((obs, oldGroup, newGroup) -> {
                if (!Objects.equals(oldGroup, newGroup)) {
                    if (chapterListView != null) {
                        chapterListView.getSelectionModel().clearSelection();
                    }
                    displayChapter(null);
                    refreshChapterList();
                }
            });
            chapterGroupComboBox.setDisable(true);
        }

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

        clearQuestDetails();
        System.out.println("[VERIFY] ChapterEditorController initialized.");
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
        if (currentChapter == null) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("No Chapter Selected");
            alert.setHeaderText("Select a chapter before creating quests");
            alert.setContentText("Choose a chapter from the list, then try again.");
            alert.showAndWait();
            if (questContextMenu != null) {
                questContextMenu.hide();
            }
            return;
        }

        double snappedX = Math.round(x / GRID_SIZE) * GRID_SIZE;
        double snappedY = Math.round(y / GRID_SIZE) * GRID_SIZE;

        Quest draftQuest = Quest.builder()
                .id(UUID.randomUUID().toString())
                .title("New Quest")
                .description("")
                .visibility(currentChapter.visibility())
                .build();

        Optional<Quest> created = showQuestEditorDialog(draftQuest, true);
        if (created.isPresent()) {
            Quest savedQuest = created.get();
            Chapter previousChapter = currentChapter;
            List<Quest> updatedQuests = new ArrayList<>(previousChapter.quests());
            updatedQuests.add(savedQuest);
            Chapter updatedChapter = new Chapter(previousChapter.id(), previousChapter.title(), previousChapter.icon(),
                    previousChapter.background(), updatedQuests, previousChapter.visibility());
            currentChapter = updatedChapter;
            chapterById.put(updatedChapter.id(), updatedChapter);
            updateChapterList(previousChapter, updatedChapter);
            replaceWorkingChapter(previousChapter, updatedChapter);

            QuestLayoutStore layoutStore = UiServiceLocator.questLayoutStore;
            if (layoutStore != null) {
                try {
                    layoutStore.putNodePos(updatedChapter.id(), savedQuest.id(), snappedX, snappedY);
                } catch (RuntimeException e) {
                    LOGGER.log(Level.FINE, "Unable to persist quest position for " + savedQuest.id(), e);
                }
            }

            displayChapter(updatedChapter);
            selectQuest(savedQuest);
        }

        if (questContextMenu != null) {
            questContextMenu.hide();
        }
    }

    private Optional<Quest> showQuestEditorDialog(Quest quest, boolean isNew) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/dev/ftbq/editor/view/quest_editor_dialog.fxml"));
            Parent root = loader.load();
            QuestEditorDialogController controller = loader.getController();
            controller.setQuest(quest);

            Stage dialogStage = new Stage();
            dialogStage.setTitle(isNew ? "Create New Quest" : "Edit Quest");
            Scene scene = new Scene(root);
            ThemeService.apply(scene, UserSettings.get().darkTheme);
            dialogStage.setScene(scene);
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            if (questPane != null && questPane.getScene() != null) {
                dialogStage.initOwner(questPane.getScene().getWindow());
            }
            dialogStage.showAndWait();

            if (controller.wasSaved()) {
                return Optional.of(controller.getQuest());
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to open quest editor dialog", e);
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Quest Editor");
            alert.setHeaderText("Unable to open quest editor");
            alert.setContentText(e.getMessage());
            alert.showAndWait();
        }
        return Optional.empty();
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
        chapterById.clear();

        if (chapterGroupComboBox != null) {
            chapterGroupComboBox.getSelectionModel().clearSelection();
        }

        if (project != null) {
            workingChapters.addAll(project.getChapters());
            for (Chapter chapter : workingChapters) {
                chapterById.put(chapter.id(), chapter);
            }
            rebuildChapterGroups();
            if (chapterGroupComboBox != null && !chapterGroups.isEmpty()) {
                chapterGroupComboBox.getSelectionModel().selectFirst();
            }
            refreshChapterList();
        } else {
            chapterGroups.clear();
            if (chapterGroupComboBox != null) {
                chapterGroupComboBox.setDisable(true);
            }
            if (chapterListView != null) {
                chapterListView.getItems().clear();
            }
            displayChapter(null);
        }
    }

    public void focusChapter(String chapterId) {
        if (chapterId == null || chapterListView == null) {
            return;
        }
        Chapter chapter = chapterById.get(chapterId);
        if (chapter == null) {
            return;
        }

        if (chapterGroupComboBox != null && !chapterGroups.isEmpty()) {
            ChapterGroup group = findGroupForChapter(chapterId);
            if (group != null) {
                chapterGroupComboBox.getSelectionModel().select(group);
            }
        }

        if (chapterSearchField != null) {
            String query = chapterSearchField.getText();
            if (query != null && !query.isBlank() && !chapter.title().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) {
                chapterSearchField.setText("");
            }
        }

        refreshChapterList();
        chapterListView.getSelectionModel().select(chapter);
        chapterListView.scrollTo(chapter);
        displayChapter(chapter);
    }

    private ChapterGroup findGroupForChapter(String chapterId) {
        for (ChapterGroup group : chapterGroups) {
            if (group.chapterIds().contains(chapterId)) {
                return group;
            }
        }
        return null;
    }

    private void rebuildChapterGroups() {
        chapterGroups.clear();
        if (project == null) {
            if (chapterGroupComboBox != null) {
                chapterGroupComboBox.setDisable(true);
            }
            return;
        }

        QuestFile questFile = project.getQuestFile();
        if (questFile != null) {
            chapterGroups.addAll(questFile.chapterGroups());
        }

        Set<String> groupedIds = chapterGroups.stream()
                .flatMap(group -> group.chapterIds().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Chapter> ungrouped = workingChapters.stream()
                .filter(chapter -> !groupedIds.contains(chapter.id()))
                .toList();

        if (!ungrouped.isEmpty()) {
            ChapterGroup.Builder builder = ChapterGroup.builder()
                    .id(SYNTHETIC_UNGROUPED_ID)
                    .title("Ungrouped Chapters")
                    .visibility(Visibility.VISIBLE)
                    .chapterIds(ungrouped.stream().map(Chapter::id).toList());
            chapterGroups.add(builder.build());
        }

        if (chapterGroupComboBox != null) {
            chapterGroupComboBox.setDisable(chapterGroups.isEmpty());
        }
    }

    private void refreshChapterList() {
        if (chapterListView == null) {
            return;
        }
        List<Chapter> source = new ArrayList<>();
        if (chapterGroupComboBox != null && !chapterGroups.isEmpty()) {
            ChapterGroup selectedGroup = chapterGroupComboBox.getValue();
            if (selectedGroup == null) {
                chapterListView.getItems().clear();
                displayChapter(null);
                return;
            }
            for (String chapterId : selectedGroup.chapterIds()) {
                Chapter chapter = chapterById.get(chapterId);
                if (chapter != null) {
                    source.add(chapter);
                }
            }
        } else {
            source.addAll(workingChapters);
        }

        if (source.isEmpty()) {
            chapterListView.getItems().clear();
            displayChapter(null);
            return;
        }

        List<Chapter> filtered = new ArrayList<>(source);
        if (chapterSearchField != null) {
            String query = chapterSearchField.getText();
            if (query != null && !query.isBlank()) {
                String filter = query.toLowerCase(Locale.ROOT);
                filtered = source.stream()
                        .filter(chapter -> chapter.title().toLowerCase(Locale.ROOT).contains(filter))
                        .collect(Collectors.toList());
            }
        }

        ObservableList<Chapter> items = chapterListView.getItems();
        items.setAll(filtered);
        if (filtered.isEmpty()) {
            displayChapter(null);
            return;
        }

        Chapter selection = chapterListView.getSelectionModel().getSelectedItem();
        if (selection == null || !filtered.contains(selection)) {
            chapterListView.getSelectionModel().selectFirst();
            selection = chapterListView.getSelectionModel().getSelectedItem();
        }
        if (selection != null) {
            displayChapter(selection);
        } else {
            displayChapter(null);
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

    @FXML
    private void onChapterGroupSelected() {
        refreshChapterList();
    }

    private void displayChapter(Chapter chapter) {
        currentChapter = chapter;
        if (chapterTitle != null) {
            chapterTitle.setText(chapter != null ? chapter.title() : "Select a Chapter");
        }

        questNodes.clear();
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
            node.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    selectQuest(quest);
                    event.consume();
                }
            });
        }

        if (!quests.isEmpty()) {
            selectQuest(quests.get(0));
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
        if (taskList != null) {
            taskList.getItems().setAll(quest.tasks().stream()
                    .map(this::describeTask)
                    .collect(Collectors.toList()));
        }
        if (rewardList != null) {
            rewardList.getItems().setAll(quest.itemRewards().stream()
                    .map(ItemReward::describe)
                    .collect(Collectors.toList()));
        }
        if (dependencyList != null) {
            dependencyList.getItems().setAll(quest.dependencies().stream()
                    .map(this::describeDependency)
                    .collect(Collectors.toList()));
        }
        if (addTaskMenu != null) {
            addTaskMenu.setDisable(false);
        }
        if (addRewardMenu != null) {
            addRewardMenu.setDisable(false);
        }
        if (questDetailsPanel != null) {
            questDetailsPanel.setVisible(true);
            questDetailsPanel.setManaged(true);
        }
    }

    private void clearQuestDetails() {
        if (taskList != null) {
            taskList.getItems().clear();
        }
        if (rewardList != null) {
            rewardList.getItems().clear();
        }
        if (dependencyList != null) {
            dependencyList.getItems().clear();
        }
        if (addTaskMenu != null) {
            addTaskMenu.setDisable(true);
        }
        if (addRewardMenu != null) {
            addRewardMenu.setDisable(true);
        }
        if (questDetailsPanel != null) {
            questDetailsPanel.setVisible(false);
            questDetailsPanel.setManaged(false);
        }
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
        if (quest == null) {
            return;
        }

        Optional<Quest> edited = showQuestEditorDialog(quest, false);
        if (edited.isEmpty() || currentChapter == null) {
            return;
        }

        Quest updatedQuest = edited.get();
        Chapter previousChapter = currentChapter;
        List<Quest> updatedQuests = previousChapter.quests().stream()
                .map(existing -> existing.id().equals(updatedQuest.id()) ? updatedQuest : existing)
                .collect(Collectors.toCollection(ArrayList::new));
        Chapter updatedChapter = new Chapter(previousChapter.id(), previousChapter.title(), previousChapter.icon(),
                previousChapter.background(), updatedQuests, previousChapter.visibility());
        currentChapter = updatedChapter;
        chapterById.put(updatedChapter.id(), updatedChapter);
        updateChapterList(previousChapter, updatedChapter);
        replaceWorkingChapter(previousChapter, updatedChapter);
        displayChapter(updatedChapter);
        selectQuest(updatedQuest);
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
                chapterById.put(updatedChapter.id(), updatedChapter);
                return;
            }
        }
    }

    private void replaceWorkingChapter(Chapter previousChapter, Chapter updatedChapter) {
        for (int i = 0; i < workingChapters.size(); i++) {
            Chapter item = workingChapters.get(i);
            if (item.id().equals(previousChapter.id())) {
                workingChapters.set(i, updatedChapter);
                chapterById.put(updatedChapter.id(), updatedChapter);
                return;
            }
        }
        workingChapters.add(updatedChapter);
        chapterById.put(updatedChapter.id(), updatedChapter);
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
