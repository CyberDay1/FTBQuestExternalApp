package dev.ftbq.editor.controller;

import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.Reward;
import dev.ftbq.editor.domain.Task;
import dev.ftbq.editor.domain.TaskTypeRegistry;
import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.services.UiServiceLocator;
import dev.ftbq.editor.services.templates.RewardTemplates;
import dev.ftbq.editor.services.templates.TaskTemplates;
import dev.ftbq.editor.store.StoreDao;
import dev.ftbq.editor.ui.graph.GraphCanvas;
import dev.ftbq.editor.ui.graph.QuestNodeView;
import dev.ftbq.editor.view.QuestEditDialogController;
import dev.ftbq.editor.view.graph.layout.QuestLayoutStore;
import dev.ftbq.editor.viewmodel.ChapterEditorViewModel;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.Node;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.GridPane;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controller responsible for presenting a quest chapter graph.
 */
public class ChapterEditorController {

    private static final String EMPTY_TITLE = "";

    @FXML
    private AnchorPane canvasHost;

    @FXML
    private ListView<Chapter> chapterList;

    @FXML
    private TextField chapterSearchField;

    @FXML
    private Label chapterTitleLabel;

    @FXML
    private ListView<String> taskList;

    @FXML
    private ListView<String> rewardList;

    @FXML
    private ListView<String> dependencyList;

    @FXML
    private MenuButton addTaskMenu;

    @FXML
    private MenuButton addRewardMenu;

    private GraphCanvas canvas;
    private final ObservableList<String> tasks = FXCollections.observableArrayList();
    private final ObservableList<String> rewards = FXCollections.observableArrayList();
    private final ObservableList<String> dependencies = FXCollections.observableArrayList();
    private final Map<String, QuestNodeView> nodes = new HashMap<>();
    private final Map<String, Point2D> questPositions = new HashMap<>();
    private final Map<String, ChapterGraphState> chapterStates = new HashMap<>();
    private final AnchorPane nodeLayer = new AnchorPane();
    private int taskCounter = 1;
    private int rewardCounter = 1;
    private int dependencyCounter = 1;
    private ChapterEditorViewModel viewModel;
    private boolean programmaticChapterSelection;
    private Chapter currentChapter;
    private String pendingSelectionQuestId;
    private String selectedQuestId;
    private double contextX;
    private double contextY;
    private String draggingChapterId;

    @FXML
    public void initialize() {
        setupCanvas();
        configureChapterList();
        configureChapterSearch();
        configureDetailLists();
        configureAddMenus();
        if (viewModel != null && viewModel.getChapter() != null) {
            applyChapter(viewModel.getChapter());
        }
    }

    public void setViewModel(ChapterEditorViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.viewModel.chapterProperty().addListener((obs, oldChapter, newChapter) -> applyChapter(newChapter));
        if (chapterList != null) {
            chapterList.setItems(viewModel.getChapters());
            chapterList.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, newValue) -> {
                if (!programmaticChapterSelection && viewModel != null && newValue != null && !newValue.equals(viewModel.getChapter())) {
                    viewModel.loadChapter(newValue);
                }
            });
            selectChapter(viewModel.getChapter());
        }
        if (chapterSearchField != null) {
            chapterSearchField.setText(viewModel.chapterFilterProperty().get());
            viewModel.chapterFilterProperty().addListener((obs, oldValue, newValue) -> {
                if (chapterSearchField != null && !Objects.equals(chapterSearchField.getText(), newValue)) {
                    chapterSearchField.setText(newValue);
                }
            });
        }
        if (newChapterAvailable()) {
            applyChapter(viewModel.getChapter());
        }
    }

    private void setupCanvas() {
        if (canvasHost == null) {
            return;
        }
        canvas = new GraphCanvas(1600, 900);
        canvas.widthProperty().bind(canvasHost.widthProperty());
        canvas.heightProperty().bind(canvasHost.heightProperty());
        nodeLayer.setPickOnBounds(false);
        nodeLayer.setFocusTraversable(true);
        nodeLayer.addEventHandler(KeyEvent.KEY_PRESSED, this::handleNodeLayerKeyPressed);
        canvasHost.getChildren().setAll(canvas, nodeLayer);
        AnchorPane.setTopAnchor(canvas, 0.0);
        AnchorPane.setBottomAnchor(canvas, 0.0);
        AnchorPane.setLeftAnchor(canvas, 0.0);
        AnchorPane.setRightAnchor(canvas, 0.0);
        AnchorPane.setTopAnchor(nodeLayer, 0.0);
        AnchorPane.setBottomAnchor(nodeLayer, 0.0);
        AnchorPane.setLeftAnchor(nodeLayer, 0.0);
        AnchorPane.setRightAnchor(nodeLayer, 0.0);
        canvas.setOnRedraw(v -> renderNodes());
        canvas.setOnContextMenuRequested(event -> {
            contextX = event.getX();
            contextY = event.getY();
            ContextMenu menu = buildCanvasContextMenu();
            menu.show(canvas, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    private ContextMenu buildCanvasContextMenu() {
        ContextMenu menu = new ContextMenu();
        MenuItem addQuest = new MenuItem("Add Quest Here");
        addQuest.setOnAction(e -> {
            if (currentChapter == null) {
                return;
            }
            double[] world = canvas.screenToWorld(contextX, contextY);
            double snappedX = canvas.snap(world[0]);
            double snappedY = canvas.snap(world[1]);
            addQuestAt(snappedX, snappedY);
        });
        menu.getItems().add(addQuest);
        return menu;
    }

    private boolean newChapterAvailable() {
        return viewModel != null && viewModel.getChapter() != null;
    }

    private void applyChapter(Chapter chapter) {
        if (chapter == currentChapter) {
            return;
        }
        if (currentChapter != null) {
            persistCurrentChapterState();
        }
        if (chapter == null) {
            currentChapter = null;
            selectedQuestId = null;
            questPositions.clear();
            nodeLayer.getChildren().clear();
            nodes.clear();
            if (canvas != null) {
                canvas.redraw();
            }
            if (chapterTitleLabel != null) {
                chapterTitleLabel.setText(EMPTY_TITLE);
            }
            selectChapter(null);
            clearQuestDetails();
            return;
        }
        currentChapter = chapter;
        if (chapterTitleLabel != null) {
            chapterTitleLabel.setText(chapter.title());
        }
        selectChapter(chapter);
        restoreQuestPositions(chapter);
        applyCanvasState(chapter);
        if (canvas != null) {
            canvas.redraw();
        }
        populateQuestDetails(chapter);
    }

    private void restoreQuestPositions(Chapter chapter) {
        questPositions.clear();
        if (chapter == null) {
            return;
        }
        String chapterKey = chapterKey(chapter);
        ChapterGraphState cachedState = chapterStates.get(chapterKey);
        if (cachedState != null) {
            questPositions.putAll(cachedState.positions);
        } else {
            loadQuestPositionsFromStore(chapterKey, chapter);
        }
        ensureQuestPositions(chapter);
        persistAllQuestPositions(chapterKey);
    }

    private void loadQuestPositionsFromStore(String chapterKey, Chapter chapter) {
        QuestLayoutStore layoutStore = UiServiceLocator.questLayoutStore;
        if (layoutStore == null) {
            return;
        }
        for (Quest quest : chapter.quests()) {
            if (quest == null || quest.id() == null) {
                continue;
            }
            layoutStore.getNodePos(chapterKey, quest.id())
                    .ifPresent(position -> questPositions.put(quest.id(), position));
        }
    }

    private void ensureQuestPositions(Chapter chapter) {
        List<Quest> quests = chapter.quests();
        double spacing = 120.0;
        int columnCount = Math.max(1, (int) Math.ceil(Math.sqrt(Math.max(quests.size(), 1))));
        int index = 0;
        for (Quest quest : quests) {
            if (quest == null || quest.id() == null) {
                continue;
            }
            if (!questPositions.containsKey(quest.id())) {
                int row = index / columnCount;
                int col = index % columnCount;
                questPositions.put(quest.id(), new Point2D(col * spacing, row * spacing));
            }
            index++;
        }
    }

    private void applyCanvasState(Chapter chapter) {
        if (canvas == null) {
            return;
        }
        ChapterGraphState cachedState = chapterStates.get(chapterKey(chapter));
        if (cachedState != null) {
            canvas.setState(cachedState.scale, cachedState.translateX, cachedState.translateY);
        } else {
            canvas.resetView();
        }
    }

    private void persistCurrentChapterState() {
        if (currentChapter == null) {
            return;
        }
        String key = chapterKey(currentChapter);
        ChapterGraphState state = chapterStates.computeIfAbsent(key, k -> new ChapterGraphState());
        state.positions.clear();
        state.positions.putAll(questPositions);
        if (canvas != null) {
            state.scale = canvas.getScale();
            state.translateX = canvas.getPanX();
            state.translateY = canvas.getPanY();
        }
        persistAllQuestPositions(key);
    }

    private String chapterKey(Chapter chapter) {
        if (chapter == null) {
            return "";
        }
        String id = chapter.id();
        return id != null ? id : chapter.title();
    }

    private void renderNodes() {
        nodes.clear();
        nodeLayer.getChildren().clear();
        if (canvas == null || currentChapter == null) {
            return;
        }
        drawEdges(canvas.getGraphicsContext2D());
        for (Quest quest : currentChapter.quests()) {
            if (quest == null || quest.id() == null) {
                continue;
            }
            Point2D world = questPositions.getOrDefault(quest.id(), new Point2D(0, 0));
            QuestNodeView node = new QuestNodeView(quest.id(), quest.title(), world.getX(), world.getY(), canvas);
            node.setRewardSummary(quest.rewards());
            positionNode(node, world);
            configureNodeInteractions(node, quest);
            nodes.put(quest.id(), node);
            nodeLayer.getChildren().add(node);
        }
        String toSelect = pendingSelectionQuestId != null ? pendingSelectionQuestId : selectedQuestId;
        if (toSelect != null && nodes.containsKey(toSelect)) {
            selectedQuestId = toSelect;
        } else if (toSelect != null) {
            selectedQuestId = null;
        }
        pendingSelectionQuestId = null;
        updateSelectionVisuals();
    }

    private void drawEdges(GraphicsContext gc) {
        gc.setTransform(canvas.getWorld());
        gc.setStroke(canvas.getEdgeRequiredColor());
        gc.setLineWidth(1.6 / Math.max(canvas.getScale(), 0.1));
        gc.setLineDashes(0);
        gc.beginPath();
        for (Quest quest : currentChapter.quests()) {
            Point2D start = questPositions.get(quest.id());
            if (start == null) {
                continue;
            }
            for (Dependency dependency : quest.dependencies()) {
                Point2D end = questPositions.get(dependency.questId());
                if (end == null) {
                    continue;
                }
                gc.moveTo(start.getX(), start.getY());
                gc.lineTo(end.getX(), end.getY());
            }
        }
        gc.stroke();
    }

    private void positionNode(QuestNodeView node, Point2D world) {
        if (canvas == null) {
            return;
        }
        node.setWorldPosition(world.getX(), world.getY());
        node.updateScreenPosition();
    }

    private void configureNodeInteractions(QuestNodeView node, Quest quest) {
        node.setOnMove((id, worldX, worldY) -> handleNodeMove(id, worldX, worldY));
        node.setOnEdit(id -> showQuestEditDialog(quest));
        node.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1 && !event.isConsumed()) {
                selectQuest(node.questId);
                nodeLayer.requestFocus();
            }
        });
        node.setOnContextMenuRequested(event -> {
            ContextMenu menu = buildNodeContextMenu(node.questId);
            menu.show(node, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    private void handleNodeLayerKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER && openSelectedQuestForEditing()) {
            event.consume();
        }
    }

    private boolean openSelectedQuestForEditing() {
        if (selectedQuestId == null || currentChapter == null || currentChapter.quests() == null) {
            return false;
        }
        for (Quest quest : currentChapter.quests()) {
            if (quest != null && Objects.equals(quest.id(), selectedQuestId)) {
                showQuestEditDialog(quest);
                return true;
            }
        }
        return false;
    }

    private ContextMenu buildNodeContextMenu(String questId) {
        ContextMenu menu = new ContextMenu();
        MenuItem delete = new MenuItem("Delete Quest");
        delete.setOnAction(e -> deleteQuest(questId));
        Menu connectMenu = new Menu("Connect →");
        if (currentChapter != null) {
            for (Quest quest : currentChapter.quests()) {
                if (quest == null || quest.id() == null || quest.id().equals(questId)) {
                    continue;
                }
                MenuItem targetItem = new MenuItem(quest.title());
                targetItem.setOnAction(evt -> connectQuests(questId, quest.id(), true));
                connectMenu.getItems().add(targetItem);
            }
        }
        if (connectMenu.getItems().isEmpty()) {
            connectMenu.setDisable(true);
        }
        Menu moveMenu = new Menu("Move to Chapter");
        if (viewModel != null) {
            for (Chapter chapter : viewModel.getChapters()) {
                if (chapter == null || (currentChapter != null && Objects.equals(chapter.id(), currentChapter.id()))) {
                    continue;
                }
                MenuItem target = new MenuItem(chapter.title());
                target.setOnAction(evt -> moveQuestToChapter(questId, chapter));
                moveMenu.getItems().add(target);
            }
        }
        if (moveMenu.getItems().isEmpty()) {
            moveMenu.setDisable(true);
        }
        menu.getItems().addAll(delete, connectMenu, moveMenu);
        return menu;
    }

    private void handleNodeMove(String questId, double worldX, double worldY) {
        if (canvas == null) {
            return;
        }
        double snappedX = canvas.snap(worldX);
        double snappedY = canvas.snap(worldY);
        questPositions.put(questId, new Point2D(snappedX, snappedY));
        ChapterGraphState state = chapterStates.get(chapterKey(currentChapter));
        if (state != null) {
            state.positions.put(questId, new Point2D(snappedX, snappedY));
        }
        persistQuestPosition(questId, snappedX, snappedY);
        pendingSelectionQuestId = questId;
        if (canvas != null) {
            canvas.redraw();
        }
    }

    private void persistQuestPosition(String questId, double x, double y) {
        if (currentChapter == null) {
            return;
        }
        QuestLayoutStore layoutStore = UiServiceLocator.questLayoutStore;
        if (layoutStore != null) {
            layoutStore.putNodePos(chapterKey(currentChapter), questId, x, y);
        }
    }

    private void updateSelectionVisuals() {
        if (selectedQuestId == null) {
            nodes.values().forEach(view -> view.setSelected(false));
            return;
        }
        nodes.values().forEach(view -> view.setSelected(Objects.equals(view.questId, selectedQuestId)));
    }

    private void selectQuest(String questId) {
        selectedQuestId = questId;
        updateSelectionVisuals();
    }

    private static final class ChapterGraphState {
        private final Map<String, Point2D> positions = new HashMap<>();
        private double scale = 1.0;
        private double translateX;
        private double translateY;
    }

    private void clearSelection() {
        selectedQuestId = null;
        updateSelectionVisuals();
    }

    private void moveQuestToChapter(String questId, Chapter targetChapter) {
        if (viewModel == null || targetChapter == null) {
            return;
        }
        String previousChapterKey = currentChapter != null ? chapterKey(currentChapter) : null;
        viewModel.moveQuestToChapter(questId, targetChapter);
        pendingSelectionQuestId = null;
        selectedQuestId = null;
        QuestLayoutStore layoutStore = UiServiceLocator.questLayoutStore;
        if (layoutStore != null && previousChapterKey != null) {
            layoutStore.removeQuest(previousChapterKey, questId);
        }
        applyChapter(viewModel.getChapter());
    }

    private void configureChapterList() {
        if (chapterList == null) {
            return;
        }
        chapterList.setCellFactory(listView -> new ChapterListCell());
        chapterList.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        chapterList.setOnDragOver(event -> {
            if (draggingChapterId != null) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });
        chapterList.setOnDragDropped(event -> {
            if (draggingChapterId == null || viewModel == null) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }
            Optional<Chapter> source = findChapterById(draggingChapterId);
            if (source.isEmpty()) {
                event.setDropCompleted(false);
                event.consume();
                return;
            }
            int targetIndex = chapterList.getItems().isEmpty() ? 0 : chapterList.getItems().size() - 1;
            viewModel.reorderChapter(source.get(), targetIndex);
            chapterList.getSelectionModel().select(source.get());
            event.setDropCompleted(true);
            draggingChapterId = null;
            event.consume();
        });
    }

    private void configureChapterSearch() {
        if (chapterSearchField == null) {
            return;
        }
        chapterSearchField.textProperty().addListener((obs, oldValue, newValue) -> {
            if (viewModel != null && !Objects.equals(viewModel.chapterFilterProperty().get(), newValue)) {
                viewModel.chapterFilterProperty().set(newValue);
            }
        });
    }

    private void selectChapter(Chapter chapter) {
        if (chapterList == null) {
            return;
        }
        try {
            programmaticChapterSelection = true;
            if (chapter == null) {
                chapterList.getSelectionModel().clearSelection();
            } else {
                chapterList.getSelectionModel().select(chapter);
                chapterList.scrollTo(chapter);
            }
        } finally {
            programmaticChapterSelection = false;
        }
    }

    private Optional<Chapter> findChapterById(String chapterId) {
        if (viewModel == null || chapterId == null) {
            return Optional.empty();
        }
        return viewModel.getChapters().stream()
                .filter(chapter -> chapter != null && chapterId.equals(chapter.id()))
                .findFirst();
    }

    private void configureDetailLists() {
        configureListView(taskList, tasks, "No tasks defined");
        configureListView(rewardList, rewards, "No rewards defined");
        configureListView(dependencyList, dependencies, "No dependencies defined");
    }

    private void configureAddMenus() {
        configureTaskMenu();
        configureRewardMenu();
    }

    private void configureTaskMenu() {
        if (addTaskMenu == null) {
            return;
        }
        addTaskMenu.getItems().clear();
        for (String id : TaskTypeRegistry.ids()) {
            MenuItem item = new MenuItem(id);
            item.setOnAction(event -> addTaskFromTemplate(id));
            addTaskMenu.getItems().add(item);
        }
    }

    private void configureRewardMenu() {
        if (addRewardMenu == null) {
            return;
        }
        addRewardMenu.getItems().clear();

        MenuItem xpLevels = new MenuItem("XP Levels");
        xpLevels.setOnAction(event -> promptForInteger("Add XP Levels", "Levels", 5)
                .map(value -> Math.max(0, value))
                .ifPresent(levels -> addRewardFromTemplate(RewardTemplates.xpLevels(levels))));

        MenuItem xpAmount = new MenuItem("XP Amount");
        xpAmount.setOnAction(event -> promptForInteger("Add XP Amount", "Amount", 100)
                .map(value -> Math.max(0, value))
                .ifPresent(amount -> addRewardFromTemplate(RewardTemplates.xpAmount(amount))));

        MenuItem lootTable = new MenuItem("Loot Table");
        lootTable.setOnAction(event -> promptForString("Add Loot Table", "Loot table ID", "mod:loot/example")
                .ifPresent(id -> addRewardFromTemplate(RewardTemplates.lootTable(id))));

        MenuItem command = new MenuItem("Command");
        command.setOnAction(event -> promptForCommandReward().ifPresent(this::addRewardFromTemplate));

        addRewardMenu.getItems().addAll(xpLevels, xpAmount, lootTable, command);
    }

    private void configureListView(ListView<String> listView, ObservableList<String> items, String placeholderText) {
        if (listView == null) {
            return;
        }
        listView.setItems(items);
        listView.setPlaceholder(new Label(placeholderText));
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.setOnKeyPressed(event -> {
            if ((event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE)
                    && !listView.getSelectionModel().getSelectedItems().isEmpty()) {
                List<String> selected = List.copyOf(listView.getSelectionModel().getSelectedItems());
                items.removeAll(selected);
                event.consume();
            }
        });
    }

    private void addTaskFromTemplate(String id) {
        Task template = switch (id) {
            case "item" -> TaskTemplates.item("minecraft:stone", 1);
            case "advancement" -> TaskTemplates.advancement("minecraft:story/root");
            case "location" -> TaskTemplates.location(0, 64, 0, "minecraft:overworld");
            default -> null;
        };
        if (template == null) {
            return;
        }
        tasks.add("Task %d: %s".formatted(taskCounter++, template.describe()));
    }

    private void addRewardFromTemplate(Reward reward) {
        if (reward == null) {
            return;
        }
        rewards.add("Reward %d: %s".formatted(rewardCounter++, reward.describe()));
    }

    private Optional<Integer> promptForInteger(String title, String prompt, int defaultValue) {
        TextInputDialog dialog = new TextInputDialog(Integer.toString(defaultValue));
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(prompt);
        return dialog.showAndWait()
                .map(String::trim)
                .filter(input -> !input.isEmpty())
                .flatMap(input -> {
                    try {
                        return Optional.of(Integer.parseInt(input));
                    } catch (NumberFormatException ex) {
                        return Optional.empty();
                    }
                });
    }

    private Optional<String> promptForString(String title, String prompt, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue);
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(prompt);
        return dialog.showAndWait()
                .map(String::trim)
                .filter(value -> !value.isEmpty());
    }

    private Optional<Reward> promptForCommandReward() {
        Dialog<Reward> dialog = new Dialog<>();
        dialog.setTitle("Add Command Reward");
        dialog.setHeaderText(null);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        GridPane content = new GridPane();
        content.setHgap(8);
        content.setVgap(8);
        Label commandLabel = new Label("Command");
        TextField commandField = new TextField();
        CheckBox runAsServer = new CheckBox("Run as server");
        content.add(commandLabel, 0, 0);
        content.add(commandField, 1, 0);
        content.add(runAsServer, 1, 1);
        GridPane.setMargin(runAsServer, new Insets(4, 0, 0, 0));

        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                String commandText = commandField.getText() == null ? "" : commandField.getText().trim();
                if (!commandText.isEmpty()) {
                    try {
                        return RewardTemplates.command(commandText, runAsServer.isSelected());
                    } catch (IllegalArgumentException ex) {
                        return null;
                    }
                }
            }
            return null;
        });
        return dialog.showAndWait().filter(Objects::nonNull);
    }

    private void clearQuestDetails() {
        tasks.clear();
        rewards.clear();
        dependencies.clear();
        taskCounter = 1;
        rewardCounter = 1;
        dependencyCounter = 1;
    }

    private void populateQuestDetails(Chapter chapter) {
        clearQuestDetails();
        if (chapter == null || chapter.quests() == null) {
            return;
        }
        for (Quest quest : chapter.quests()) {
            if (quest == null) {
                continue;
            }
            for (Task task : quest.tasks()) {
                tasks.add(formatTaskEntry(quest, task));
            }
            for (Reward reward : quest.rewards()) {
                rewards.add(formatRewardEntry(quest, reward));
            }
            for (Dependency dependency : quest.dependencies()) {
                dependencies.add(formatDependencyEntry(quest, dependency));
            }
        }
        taskCounter = tasks.size() + 1;
        rewardCounter = rewards.size() + 1;
        dependencyCounter = dependencies.size() + 1;
    }

    private String formatTaskEntry(Quest quest, Task task) {
        return "%s: %s".formatted(quest.title(), task.describe());
    }

    private String formatRewardEntry(Quest quest, Reward reward) {
        return "%s: %s".formatted(quest.title(), reward.describe());
    }

    private String formatDependencyEntry(Quest quest, Dependency dependency) {
        String requirement = dependency.required() ? "required" : "optional";
        return "%s → %s (%s)".formatted(quest.title(), dependency.questId(), requirement);
    }

    @FXML
    private void onAddDependency() {
        dependencies.add("Dependency %d".formatted(dependencyCounter++));
    }

    @FXML
    private void onRemoveQuest() {
        if (currentChapter == null || selectedQuestId == null) {
            return;
        }
        deleteQuest(selectedQuestId);
    }

    private void deleteQuest(String questId) {
        if (currentChapter == null) {
            return;
        }
        Quest toRemove = currentChapter.quests().stream()
                .filter(q -> Objects.equals(q.id(), questId))
                .findFirst()
                .orElse(null);
        if (toRemove == null) {
            return;
        }
        List<Quest> updatedQuests = new ArrayList<>();
        List<Quest> questsToPersist = new ArrayList<>();
        for (Quest quest : currentChapter.quests()) {
            if (quest.id().equals(questId)) {
                continue;
            }
            Quest cleaned = removeDependencyReferences(quest, questId);
            updatedQuests.add(cleaned);
            if (cleaned != quest) {
                questsToPersist.add(cleaned);
            }
        }
        Chapter updatedChapter = rebuildChapter(currentChapter, updatedQuests);
        questPositions.remove(questId);
        QuestLayoutStore layoutStore = UiServiceLocator.questLayoutStore;
        if (layoutStore != null) {
            layoutStore.removeQuest(chapterKey(currentChapter), questId);
        }
        clearSelection();
        applyUpdatedChapter(updatedChapter);
        StoreDao dao = UiServiceLocator.storeDao;
        if (dao != null) {
            dao.deleteQuest(questId);
            questsToPersist.forEach(dao::saveQuest);
        }
        if (canvas != null) {
            canvas.redraw();
        }
    }

    private void connectQuests(String sourceId, String targetId, boolean required) {
        if (Objects.equals(sourceId, targetId) || currentChapter == null) {
            return;
        }
        Quest source = currentChapter.quests().stream()
                .filter(q -> Objects.equals(q.id(), sourceId))
                .findFirst()
                .orElse(null);
        if (source == null) {
            return;
        }
        boolean alreadyLinked = source.dependencies().stream()
                .anyMatch(dep -> Objects.equals(dep.questId(), targetId));
        if (alreadyLinked) {
            return;
        }
        List<Dependency> deps = new ArrayList<>(source.dependencies());
        deps.add(new Dependency(targetId, required));
        Quest updatedSource = Quest.builder()
                .id(source.id())
                .title(source.title())
                .description(source.description())
                .icon(source.icon())
                .visibility(source.visibility())
                .tasks(source.tasks())
                .rewards(source.rewards())
                .dependencies(deps)
                .build();
        List<Quest> updatedQuests = currentChapter.quests().stream()
                .map(q -> Objects.equals(q.id(), sourceId) ? updatedSource : q)
                .toList();
        Chapter updatedChapter = rebuildChapter(currentChapter, updatedQuests);
        pendingSelectionQuestId = sourceId;
        applyUpdatedChapter(updatedChapter);
        StoreDao dao = UiServiceLocator.storeDao;
        if (dao != null) {
            dao.saveQuest(updatedSource);
        }
        if (canvas != null) {
            canvas.redraw();
        }
    }

    private void addQuestAt(double worldX, double worldY) {
        if (currentChapter == null) {
            return;
        }
        Quest newQuest = Quest.builder()
                .id(generateQuestId(currentChapter))
                .title(generateQuestTitle(currentChapter))
                .build();
        List<Quest> updatedQuests = new ArrayList<>(currentChapter.quests());
        updatedQuests.add(newQuest);
        Chapter updatedChapter = rebuildChapter(currentChapter, updatedQuests);
        questPositions.put(newQuest.id(), new Point2D(worldX, worldY));
        persistQuestPosition(newQuest.id(), worldX, worldY);
        pendingSelectionQuestId = newQuest.id();
        applyUpdatedChapter(updatedChapter);
        StoreDao dao = UiServiceLocator.storeDao;
        if (dao != null) {
            dao.saveQuest(newQuest);
        }
        if (canvas != null) {
            canvas.redraw();
        }
    }

    private final class ChapterListCell extends ListCell<Chapter> {

        private ChapterListCell() {
            setOnDragDetected(event -> {
                Chapter item = getItem();
                if (item == null) {
                    return;
                }
                draggingChapterId = item.id();
                Dragboard dragboard = startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(draggingChapterId);
                dragboard.setContent(content);
                event.consume();
            });
            setOnDragOver(event -> {
                Chapter item = getItem();
                if (draggingChapterId != null && item != null && !draggingChapterId.equals(item.id())) {
                    event.acceptTransferModes(TransferMode.MOVE);
                }
                event.consume();
            });
            setOnDragDropped(event -> {
                if (draggingChapterId == null || viewModel == null) {
                    event.setDropCompleted(false);
                    event.consume();
                    return;
                }
                Chapter target = getItem();
                if (target == null || draggingChapterId.equals(target.id())) {
                    event.setDropCompleted(false);
                    event.consume();
                    return;
                }
                Optional<Chapter> source = findChapterById(draggingChapterId);
                if (source.isEmpty()) {
                    event.setDropCompleted(false);
                    event.consume();
                    return;
                }
                viewModel.reorderChapter(source.get(), getIndex());
                chapterList.getSelectionModel().select(source.get());
                event.setDropCompleted(true);
                draggingChapterId = null;
                event.consume();
            });
            setOnDragDone(event -> draggingChapterId = null);
        }

        @Override
        protected void updateItem(Chapter chapter, boolean empty) {
            super.updateItem(chapter, empty);
            if (empty || chapter == null) {
                setText(EMPTY_TITLE);
            } else {
                setText(chapter.title());
            }
        }
    }

    private void showQuestEditDialog(Quest quest) {
        if (quest == null) {
            return;
        }
        QuestEditDialogController dialogController = new QuestEditDialogController();
        dialogController.editQuest(quest).ifPresent(this::saveEditedQuest);
    }

    private void saveEditedQuest(Quest updatedQuest) {
        if (currentChapter == null || updatedQuest == null) {
            return;
        }
        List<Quest> quests = new ArrayList<>(currentChapter.quests().size());
        boolean replaced = false;
        for (Quest quest : currentChapter.quests()) {
            if (quest.id().equals(updatedQuest.id())) {
                quests.add(updatedQuest);
                replaced = true;
            } else {
                quests.add(quest);
            }
        }
        if (!replaced) {
            quests.add(updatedQuest);
        }
        Chapter updatedChapter = rebuildChapter(currentChapter, quests);
        pendingSelectionQuestId = updatedQuest.id();
        applyUpdatedChapter(updatedChapter);
        StoreDao dao = UiServiceLocator.storeDao;
        if (dao != null) {
            dao.saveQuest(updatedQuest);
        }
        if (canvas != null) {
            canvas.redraw();
        }
    }

    private Chapter rebuildChapter(Chapter original, List<Quest> quests) {
        return Chapter.builder()
                .id(original.id())
                .title(original.title())
                .icon(original.icon())
                .background(original.background())
                .visibility(original.visibility())
                .quests(quests)
                .build();
    }

    private void applyUpdatedChapter(Chapter updatedChapter) {
        if (viewModel != null) {
            viewModel.setChapter(updatedChapter);
        } else {
            applyChapter(updatedChapter);
        }
    }

    private void persistAllQuestPositions(String chapterKey) {
        if (chapterKey == null || chapterKey.isBlank()) {
            return;
        }
        QuestLayoutStore layoutStore = UiServiceLocator.questLayoutStore;
        if (layoutStore == null) {
            return;
        }
        questPositions.forEach((questId, position) -> {
            if (questId != null && position != null) {
                layoutStore.putNodePos(chapterKey, questId, position.getX(), position.getY());
            }
        });
    }

    private String generateQuestId(Chapter chapter) {
        Set<String> existingIds = chapter.quests().stream()
                .map(Quest::id)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        String baseId = "new_quest";
        if (!existingIds.contains(baseId)) {
            return baseId;
        }
        int counter = 2;
        while (true) {
            String candidate = "%s_%d".formatted(baseId, counter);
            if (!existingIds.contains(candidate)) {
                return candidate;
            }
            counter++;
        }
    }

    private String generateQuestTitle(Chapter chapter) {
        Set<String> existingTitles = chapter.quests().stream()
                .map(Quest::title)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        String baseTitle = "New Quest";
        if (!existingTitles.contains(baseTitle)) {
            return baseTitle;
        }
        int counter = 2;
        while (true) {
            String candidate = "%s %d".formatted(baseTitle, counter);
            if (!existingTitles.contains(candidate)) {
                return candidate;
            }
            counter++;
        }
    }

    private Quest removeDependencyReferences(Quest quest, String questId) {
        List<Dependency> filteredDependencies = quest.dependencies().stream()
                .filter(dependency -> !Objects.equals(dependency.questId(), questId))
                .toList();
        if (filteredDependencies.size() == quest.dependencies().size()) {
            return quest;
        }
        return Quest.builder()
                .id(quest.id())
                .title(quest.title())
                .description(quest.description())
                .icon(quest.icon())
                .visibility(quest.visibility())
                .tasks(quest.tasks())
                .rewards(quest.rewards())
                .dependencies(filteredDependencies)
                .build();
    }
}


