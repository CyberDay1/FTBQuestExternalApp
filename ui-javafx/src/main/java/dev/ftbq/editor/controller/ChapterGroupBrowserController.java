package dev.ftbq.editor.controller;

import dev.ftbq.editor.MainApp;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.services.UiServiceLocator;
import dev.ftbq.editor.viewmodel.ChapterGroupBrowserViewModel;
import dev.ftbq.editor.viewmodel.ChapterGroupBrowserViewModel.Chapter;
import dev.ftbq.editor.viewmodel.ChapterGroupBrowserViewModel.ChapterGroup;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.util.Callback;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ChapterGroupBrowserController {
    private static final Logger LOGGER = Logger.getLogger(ChapterGroupBrowserController.class.getName());

    @FXML
    private TextField searchField;

    @FXML
    private TreeView<TreeNodeData> chapterTree;

    private TreeItem<TreeNodeData> rootItem = new TreeItem<>(new TreeNodeData("root", NodeType.ROOT, null, null, null));

    private ChapterGroupBrowserViewModel viewModel;
    private Chapter draggingChapter;
    private ChapterGroup draggingFromGroup;
    private Path workspace;
    private QuestFile questFile;
    private MainApp mainApp;

    private final ListChangeListener<ChapterGroup> groupsListener = change -> {
        while (change.next()) {
            if (change.wasAdded()) {
                change.getAddedSubList().forEach(this::attachGroupListener);
            }
        }
        updateTree();
    };

    public void setViewModel(ChapterGroupBrowserViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        bindViewModel();
    }

    public void setWorkspaceContext(Path workspace, QuestFile questFile) {
        this.workspace = workspace;
        this.questFile = questFile;
    }

    public void setMainApp(MainApp mainApp) {
        this.mainApp = mainApp;
        if (UiServiceLocator.storeDao != null) {
            reloadGroups();
        }
    }

    @FXML
    public void initialize() {
        if (viewModel == null) {
            setViewModel(new ChapterGroupBrowserViewModel());
        }
        chapterTree.setRoot(rootItem);
        chapterTree.setShowRoot(false);
        chapterTree.setCellFactory(createCellFactory());
        chapterTree.setOnContextMenuRequested(this::handleTreeContextMenuRequest);
        chapterTree.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                Quest quest = getSelectedQuest();
                if (quest != null && mainApp != null) {
                    mainApp.openQuestEditor(quest);
                }
            }
        });
    }

    public void reloadGroups() {
        if (chapterTree == null) {
            LOGGER.warning("Chapter tree view not initialized; cannot reload chapter groups.");
            return;
        }
        if (UiServiceLocator.storeDao == null) {
            LOGGER.warning("StoreDao not initialized; cannot reload chapter groups.");
            return;
        }
        if (viewModel == null) {
            LOGGER.warning("View model not initialized; cannot reload chapter groups.");
            return;
        }
        if (questFile != null) {
            viewModel.loadFromQuestFile(questFile);
        }
        rootItem = buildTree(viewModel.getChapterGroups());
        chapterTree.setRoot(rootItem);
        chapterTree.setShowRoot(false);
    }

    public Quest getSelectedQuest() {
        if (chapterTree == null) {
            return null;
        }
        TreeItem<TreeNodeData> selectedItem = chapterTree.getSelectionModel().getSelectedItem();
        if (selectedItem == null) {
            return null;
        }
        TreeNodeData data = selectedItem.getValue();
        if (data == null || data.type() != NodeType.QUEST) {
            return null;
        }
        return data.quest();
    }

    private void bindViewModel() {
        searchField.textProperty().bindBidirectional(viewModel.searchTextProperty());
        viewModel.getChapterGroups().addListener(groupsListener);
        viewModel.searchTextProperty().addListener((obs, oldValue, newValue) -> updateTree());
        attachExistingGroupListeners(viewModel.getChapterGroups());
        updateTree();
    }

    private void attachExistingGroupListeners(List<ChapterGroup> groups) {
        for (ChapterGroup group : groups) {
            attachGroupListener(group);
        }
    }

    private void attachGroupListener(ChapterGroup group) {
        group.nameProperty().addListener(nameListener);
        group.getChapters().forEach(chapter -> chapter.nameProperty().addListener(nameListener));
        group.getChapters().addListener((ListChangeListener<Chapter>) change -> {
            while (change.next()) {
                if (change.wasAdded()) {
                    change.getAddedSubList().forEach(chapter -> chapter.nameProperty().addListener(nameListener));
                }
            }
            updateTree();
        });
    }

    private final ChangeListener<String> nameListener = (obs, oldValue, newValue) -> updateTree();

    private Callback<TreeView<TreeNodeData>, TreeCell<TreeNodeData>> createCellFactory() {
        return treeView -> new TreeCell<>() {
            {
                setOnDragDetected(event -> {
                    if (viewModel == null) {
                        return;
                    }
                    TreeNodeData data = getItem();
                    if (data != null && data.type() == NodeType.CHAPTER && data.chapter() != null) {
                        draggingChapter = data.chapter();
                        draggingFromGroup = data.group();
                        Dragboard dragboard = startDragAndDrop(TransferMode.MOVE);
                        ClipboardContent content = new ClipboardContent();
                        content.putString(draggingChapter.getName());
                        dragboard.setContent(content);
                        event.consume();
                    }
                });

                setOnDragOver(event -> {
                    if (draggingChapter == null) {
                        return;
                    }
                    TreeNodeData data = getItem();
                    if (data == null) {
                        return;
                    }
                    if (data.type() == NodeType.GROUP && data.group() != null && data.group() != draggingFromGroup) {
                        event.acceptTransferModes(TransferMode.MOVE);
                        event.consume();
                    } else if (data.type() == NodeType.CHAPTER && data.group() != null && data.group() != draggingFromGroup) {
                        event.acceptTransferModes(TransferMode.MOVE);
                        event.consume();
                    }
                });

                setOnDragDropped(event -> {
                    boolean success = false;
                    if (draggingChapter != null && viewModel != null) {
                        TreeNodeData data = getItem();
                        if (data != null) {
                            if (data.type() == NodeType.GROUP && data.group() != null && data.group() != draggingFromGroup) {
                                ChapterGroup targetGroup = data.group();
                                viewModel.moveChapterToGroup(draggingChapter, targetGroup, targetGroup.getChapters().size());
                                success = true;
                            } else if (data.type() == NodeType.CHAPTER && data.group() != null && data.group() != draggingFromGroup) {
                                ChapterGroup targetGroup = data.group();
                                int targetIndex = targetGroup.getChapters().indexOf(data.chapter());
                                viewModel.moveChapterToGroup(draggingChapter, targetGroup, targetIndex);
                                success = true;
                            }
                        }
                    }
                    event.setDropCompleted(success);
                    resetDragState();
                    event.consume();
                });

                setOnDragDone(event -> {
                    resetDragState();
                    event.consume();
                });
            }

            @Override
            protected void updateItem(TreeNodeData item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || item.type() == NodeType.ROOT) {
                    setText(null);
                    setContextMenu(null);
                } else {
                    setText(item.name());
                    setContextMenu(buildContextMenu(getTreeItem()));
                }
            }
        };
    }

    private void resetDragState() {
        draggingChapter = null;
        draggingFromGroup = null;
    }

    private void handleTreeContextMenuRequest(ContextMenuEvent event) {
        if (chapterTree.getSelectionModel().getSelectedItem() == null) {
            ContextMenu contextMenu = new ContextMenu();
            MenuItem addGroupItem = new MenuItem("Add Chapter Group");
            addGroupItem.setDisable(!isQuestDataReady());
            addGroupItem.setOnAction(evt -> promptAddGroup());
            contextMenu.getItems().add(addGroupItem);
            contextMenu.show(chapterTree, event.getScreenX(), event.getScreenY());
            event.consume();
        }
    }

    private ContextMenu buildContextMenu(TreeItem<TreeNodeData> treeItem) {
        TreeNodeData data = treeItem.getValue();
        if (data.type() == NodeType.GROUP) {
            return buildGroupContextMenu(treeItem, data.group());
        } else if (data.type() == NodeType.CHAPTER) {
            return buildChapterContextMenu(treeItem, data.group(), data.chapter());
        }
        return null;
    }

    private ContextMenu buildGroupContextMenu(TreeItem<TreeNodeData> item, ChapterGroup group) {
        ContextMenu menu = new ContextMenu();

        MenuItem addGroup = new MenuItem("Add Chapter Group");
        addGroup.setDisable(!isQuestDataReady());
        addGroup.setOnAction(evt -> promptAddGroup());

        MenuItem addChapter = new MenuItem("Add Chapter");
        addChapter.setOnAction(evt -> promptAddChapter(group));

        MenuItem renameGroup = new MenuItem("Rename Group");
        renameGroup.setOnAction(evt -> promptRenameGroup(group));

        MenuItem removeGroup = new MenuItem("Remove Group");
        removeGroup.setOnAction(evt -> viewModel.removeGroup(group));

        MenuItem moveUp = new MenuItem("Move Group Up");
        moveUp.disableProperty().bind(Bindings.createBooleanBinding(
                () -> viewModel.getChapterGroups().indexOf(group) <= 0,
                viewModel.getChapterGroups()
        ));
        moveUp.setOnAction(evt -> viewModel.moveGroupUp(group));

        MenuItem moveDown = new MenuItem("Move Group Down");
        moveDown.disableProperty().bind(Bindings.createBooleanBinding(
                () -> {
                    ObservableList<ChapterGroup> groups = viewModel.getChapterGroups();
                    return groups.indexOf(group) == groups.size() - 1;
                },
                viewModel.getChapterGroups()
        ));
        moveDown.setOnAction(evt -> viewModel.moveGroupDown(group));

        menu.getItems().addAll(addGroup, addChapter, renameGroup, removeGroup, moveUp, moveDown);
        return menu;
    }

    private ContextMenu buildChapterContextMenu(TreeItem<TreeNodeData> item, ChapterGroup group, Chapter chapter) {
        ContextMenu menu = new ContextMenu();

        MenuItem addGroup = new MenuItem("Add Chapter Group");
        addGroup.setDisable(!isQuestDataReady());
        addGroup.setOnAction(evt -> promptAddGroup());

        MenuItem addChapter = new MenuItem("Add Chapter");
        addChapter.setOnAction(evt -> promptAddChapter(group));

        MenuItem renameChapter = new MenuItem("Rename Chapter");
        renameChapter.setOnAction(evt -> promptRenameChapter(chapter));

        MenuItem removeChapter = new MenuItem("Remove Chapter");
        removeChapter.setOnAction(evt -> viewModel.removeChapter(group, chapter));

        MenuItem moveUp = new MenuItem("Move Chapter Up");
        moveUp.disableProperty().bind(Bindings.createBooleanBinding(
                () -> group.getChapters().indexOf(chapter) <= 0,
                group.getChapters()
        ));
        moveUp.setOnAction(evt -> viewModel.moveChapterUp(group, chapter));

        MenuItem moveDown = new MenuItem("Move Chapter Down");
        moveDown.disableProperty().bind(Bindings.createBooleanBinding(
                () -> group.getChapters().indexOf(chapter) == group.getChapters().size() - 1,
                group.getChapters()
        ));
        moveDown.setOnAction(evt -> viewModel.moveChapterDown(group, chapter));

        menu.getItems().addAll(addGroup, addChapter, renameChapter, removeChapter, moveUp, moveDown);
        return menu;
    }

    private void updateTree() {
        if (chapterTree == null || viewModel == null) {
            return;
        }
        rootItem = buildTree(viewModel.getChapterGroups());
        chapterTree.setRoot(rootItem);
        chapterTree.setShowRoot(false);
    }

    private TreeItem<TreeNodeData> buildTree(List<ChapterGroup> groups) {
        TreeItem<TreeNodeData> root = new TreeItem<>(new TreeNodeData("root", NodeType.ROOT, null, null, null));
        if (groups == null || viewModel == null) {
            return root;
        }
        String search = viewModel.searchTextProperty().get();
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ENGLISH);
        LinkedHashMap<String, dev.ftbq.editor.domain.Chapter> domainChapters = buildChapterLookup();
        for (ChapterGroup group : groups) {
            TreeItem<TreeNodeData> groupItem = new TreeItem<>(new TreeNodeData(group.getName(), NodeType.GROUP, group, null, null));
            boolean groupMatches = matchesQuery(group.getName(), query);
            List<TreeItem<TreeNodeData>> chapterItems = new ArrayList<>();

            for (Chapter chapter : group.getChapters()) {
                String chapterName = Optional.ofNullable(chapter.getName()).orElse("");
                boolean chapterMatches = matchesQuery(chapterName, query);
                TreeItem<TreeNodeData> chapterItem = new TreeItem<>(new TreeNodeData(chapterName, NodeType.CHAPTER, group, chapter, null));
                dev.ftbq.editor.domain.Chapter domainChapter = domainChapters.get(chapterName);
                List<TreeItem<TreeNodeData>> questItems = new ArrayList<>();
                if (domainChapter != null) {
                    questItems = domainChapter.quests().stream()
                            .filter(Objects::nonNull)
                            .filter(quest -> query.isEmpty() || chapterMatches || groupMatches || matchesQuery(quest.title(), query))
                            .map(quest -> new TreeItem<>(new TreeNodeData(quest.title(), NodeType.QUEST, group, chapter, quest)))
                            .collect(Collectors.toCollection(ArrayList::new));
                }
                boolean includeChapter = query.isEmpty() || chapterMatches || groupMatches || !questItems.isEmpty();
                if (includeChapter) {
                    chapterItem.getChildren().addAll(questItems);
                    chapterItem.setExpanded(true);
                    chapterItems.add(chapterItem);
                }
            }

            if (groupMatches || !chapterItems.isEmpty() || query.isEmpty()) {
                groupItem.getChildren().addAll(chapterItems);
                groupItem.setExpanded(true);
                root.getChildren().add(groupItem);
            }
        }
        return root;
    }

    private LinkedHashMap<String, dev.ftbq.editor.domain.Chapter> buildChapterLookup() {
        LinkedHashMap<String, dev.ftbq.editor.domain.Chapter> lookup = new LinkedHashMap<>();
        if (questFile == null || questFile.chapters() == null) {
            return lookup;
        }
        for (dev.ftbq.editor.domain.Chapter chapter : questFile.chapters()) {
            if (chapter != null && chapter.title() != null && !chapter.title().isBlank()) {
                lookup.putIfAbsent(chapter.title(), chapter);
            }
        }
        return lookup;
    }

    private boolean matchesQuery(String value, String query) {
        if (query == null || query.isBlank()) {
            return false;
        }
        if (value == null) {
            return false;
        }
        return value.toLowerCase(Locale.ENGLISH).contains(query);
    }

    private void promptAddGroup() {
        if (!ensureQuestDataReady()) {
            return;
        }

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Chapter Group");
        dialog.setHeaderText("Create a new chapter group");
        dialog.setContentText("Group name:");
        dialog.showAndWait()
                .filter(name -> !name.isBlank())
                .ifPresent(viewModel::addGroup);
    }

    private boolean ensureQuestDataReady() {
        if (UiServiceLocator.storeDao == null) {
            LOGGER.warning("StoreDao not initialized; cannot add group.");
            showQuestDataNotLoadedAlert();
            return false;
        }
        if (workspace == null || questFile == null) {
            LOGGER.warning("Workspace or quest data not initialized; cannot add group.");
            showQuestDataNotLoadedAlert();
            return false;
        }
        if (viewModel == null) {
            LOGGER.warning("View model not initialized; cannot add group.");
            showQuestDataNotLoadedAlert();
            return false;
        }
        return true;
    }

    private boolean isQuestDataReady() {
        return UiServiceLocator.storeDao != null && workspace != null && questFile != null && viewModel != null;
    }

    private void showQuestDataNotLoadedAlert() {
        Alert alert = new Alert(AlertType.WARNING, "Cannot add chapter group: data not loaded.", ButtonType.OK);
        alert.showAndWait();
    }

    private void promptAddChapter(ChapterGroup group) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Chapter");
        dialog.setHeaderText("Create a new chapter");
        dialog.setContentText("Chapter name:");
        dialog.showAndWait()
                .filter(name -> !name.isBlank())
                .ifPresent(name -> viewModel.addChapter(group, name));
    }

    private void promptRenameGroup(ChapterGroup group) {
        TextInputDialog dialog = new TextInputDialog(group.getName());
        dialog.setTitle("Rename Group");
        dialog.setHeaderText("Rename chapter group");
        dialog.setContentText("New name:");
        dialog.showAndWait()
                .filter(name -> !name.isBlank())
                .ifPresent(name -> viewModel.renameGroup(group, name));
    }

    private void promptRenameChapter(Chapter chapter) {
        TextInputDialog dialog = new TextInputDialog(chapter.getName());
        dialog.setTitle("Rename Chapter");
        dialog.setHeaderText("Rename chapter");
        dialog.setContentText("New name:");
        dialog.showAndWait()
                .filter(name -> !name.isBlank())
                .ifPresent(name -> viewModel.renameChapter(chapter, name));
    }

    private record TreeNodeData(String name, NodeType type, ChapterGroup group, Chapter chapter, Quest quest) {
    }

    private enum NodeType {
        ROOT,
        GROUP,
        CHAPTER,
        QUEST
    }
}


