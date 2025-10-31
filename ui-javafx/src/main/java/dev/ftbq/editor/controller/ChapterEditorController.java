package dev.ftbq.editor.controller;

import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.ItemReward;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.Task;
import dev.ftbq.editor.service.ThemeService;
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
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
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

public class ChapterEditorController {
    private static final Logger LOGGER = Logger.getLogger(ChapterEditorController.class.getName());
    private static final PseudoClass SELECTED_PSEUDO_CLASS = PseudoClass.getPseudoClass("selected");
    private static final int DEFAULT_COLUMNS = 4;

    @FXML private ListView<Chapter> chapterListView;
    @FXML private TextField chapterSearchField;
    @FXML private Label chapterTitle;
    @FXML private GridPane questGrid;
    @FXML private ListView<String> taskList;
    @FXML private ListView<String> rewardList;
    @FXML private ListView<String> dependencyList;
    @FXML private MenuButton addTaskMenu;
    @FXML private MenuButton addRewardMenu;

    private Project project;
    private final List<Chapter> workingChapters = new ArrayList<>();
    private Chapter currentChapter;
    private Quest selectedQuest;
    private final Map<String, Node> questNodes = new HashMap<>();

    @FXML
    public void initialize() {
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

        if (questGrid != null) {
            questGrid.getStyleClass().add("quest-grid");
        }

        clearQuestDetails();
    }

    public void setProject(Project project) {
        this.project = project;
        workingChapters.clear();
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
        if (questGrid != null) {
            questGrid.getChildren().clear();
        }
        selectedQuest = null;
        clearQuestDetails();

        if (chapter == null || questGrid == null) {
            return;
        }

        List<Quest> quests = chapter.quests();
        for (int i = 0; i < quests.size(); i++) {
            Quest quest = quests.get(i);
            Node node = QuestNodeFactory.create(quest, this::openQuestEditor);
            int[] coordinates = resolveQuestCoordinates(chapter, quest, i);
            questGrid.add(node, coordinates[0], coordinates[1]);
            questNodes.put(quest.id(), node);
            node.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    selectQuest(quest);
                }
            });
        }

        if (!quests.isEmpty()) {
            selectQuest(quests.get(0));
        }
    }

    private int[] resolveQuestCoordinates(Chapter chapter, Quest quest, int index) {
        QuestLayoutStore layoutStore = UiServiceLocator.questLayoutStore;
        if (layoutStore != null) {
            try {
                Optional<Point2D> pos = layoutStore.getNodePos(chapter.id(), quest.id());
                if (pos.isPresent()) {
                    Point2D point = pos.get();
                    int x = Math.max(0, (int) Math.round(point.getX()));
                    int y = Math.max(0, (int) Math.round(point.getY()));
                    return new int[]{x, y};
                }
            } catch (Exception ex) {
                LOGGER.log(Level.FINE, "Unable to resolve quest coordinates for " + quest.id(), ex);
            }
        }
        int column = index % DEFAULT_COLUMNS;
        int row = index / DEFAULT_COLUMNS;
        return new int[]{column, row};
    }

    private void selectQuest(Quest quest) {
        if (quest == null) {
            questNodes.values().forEach(node -> node.pseudoClassStateChanged(SELECTED_PSEUDO_CLASS, false));
            selectedQuest = null;
            clearQuestDetails();
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
            Window owner = questGrid != null && questGrid.getScene() != null
                    ? questGrid.getScene().getWindow()
                    : null;
            if (owner != null) {
                stage.initOwner(owner);
            }
            stage.show();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to open quest editor for quest " + quest.id(), e);
        }
    }

    @FXML
    private void onQuestGridMousePressed(MouseEvent e) {
        if (e.getButton() == MouseButton.SECONDARY) {
            ContextMenu menu = new ContextMenu();
            MenuItem addQuest = new MenuItem("Add New Quest");
            addQuest.setOnAction(a -> createNewQuest());
            menu.getItems().add(addQuest);
            menu.show(questGrid, e.getScreenX(), e.getScreenY());
        }
    }

    private void createNewQuest() {
        if (currentChapter == null) {
            return;
        }

        Quest newQuest = Quest.builder()
                .id(UUID.randomUUID().toString())
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
        displayChapter(updatedChapter);
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
    private void openSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/dev/ftbq/editor/view/settings.fxml"));
            Parent root = loader.load();
            SettingsController controller = loader.getController();

            Window owner = questGrid != null && questGrid.getScene() != null ? questGrid.getScene().getWindow() : null;
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
