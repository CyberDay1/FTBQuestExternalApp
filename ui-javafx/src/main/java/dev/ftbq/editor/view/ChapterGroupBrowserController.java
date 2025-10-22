package dev.ftbq.editor.view;

import dev.ftbq.editor.viewmodel.ChapterGroupBrowserViewModel;
import dev.ftbq.editor.viewmodel.ChapterGroupBrowserViewModel.Chapter;
import dev.ftbq.editor.viewmodel.ChapterGroupBrowserViewModel.ChapterGroup;
import javafx.beans.value.ChangeListener;
import javafx.beans.binding.Bindings;
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
import javafx.scene.input.ContextMenuEvent;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ChapterGroupBrowserController {
    @FXML
    private TextField searchField;

    @FXML
    private TreeView<TreeNodeData> chapterTree;

    private final TreeItem<TreeNodeData> rootItem = new TreeItem<>(new TreeNodeData("root", NodeType.ROOT, null, null));

    private ChapterGroupBrowserViewModel viewModel;

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

    @FXML
    public void initialize() {
        chapterTree.setRoot(rootItem);
        chapterTree.setShowRoot(false);
        chapterTree.setCellFactory(createCellFactory());
        chapterTree.setOnContextMenuRequested(this::handleTreeContextMenuRequest);
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

    private void handleTreeContextMenuRequest(ContextMenuEvent event) {
        if (chapterTree.getSelectionModel().getSelectedItem() == null) {
            ContextMenu contextMenu = new ContextMenu();
            MenuItem addGroupItem = new MenuItem("Add Chapter Group");
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
        rootItem.getChildren().clear();
        String query = viewModel.searchTextProperty().get().trim().toLowerCase(Locale.ENGLISH);

        for (ChapterGroup group : viewModel.getChapterGroups()) {
            TreeItem<TreeNodeData> groupItem = new TreeItem<>(new TreeNodeData(group.getName(), NodeType.GROUP, group, null));
            boolean groupMatches = group.getName().toLowerCase(Locale.ENGLISH).contains(query);
            List<TreeItem<TreeNodeData>> chapterItems = new ArrayList<>();

            for (Chapter chapter : group.getChapters()) {
                if (query.isEmpty() || chapter.getName().toLowerCase(Locale.ENGLISH).contains(query) || groupMatches) {
                    chapterItems.add(new TreeItem<>(new TreeNodeData(chapter.getName(), NodeType.CHAPTER, group, chapter)));
                }
            }

            if (groupMatches || !chapterItems.isEmpty() || query.isEmpty()) {
                groupItem.getChildren().addAll(chapterItems);
                groupItem.setExpanded(true);
                rootItem.getChildren().add(groupItem);
            }
        }
    }

    private void promptAddGroup() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Chapter Group");
        dialog.setHeaderText("Create a new chapter group");
        dialog.setContentText("Group name:");
        dialog.showAndWait()
                .filter(name -> !name.isBlank())
                .ifPresent(viewModel::addGroup);
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

    private record TreeNodeData(String name, NodeType type, ChapterGroup group, Chapter chapter) {
    }

    private enum NodeType {
        ROOT,
        GROUP,
        CHAPTER
    }
}
