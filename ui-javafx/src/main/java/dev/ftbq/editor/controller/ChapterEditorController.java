package dev.ftbq.editor.controller;

import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.ItemReward;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.Task;
import dev.ftbq.editor.service.ThemeService;
import dev.ftbq.editor.service.UserSettings;
import dev.ftbq.editor.store.Project;
import dev.ftbq.editor.ui.graph.QuestCanvas;
import javafx.collections.FXCollections;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextField;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ChapterEditorController {
    @FXML private StackPane canvasHolder;
    @FXML private ListView<Chapter> chapterListView;
    @FXML private Label chapterTitleLabel;
    @FXML private TextField chapterSearchField;
    @FXML private ListView<String> taskList;
    @FXML private ListView<String> rewardList;
    @FXML private ListView<String> dependencyList;
    @FXML private MenuButton addTaskMenu;
    @FXML private MenuButton addRewardMenu;
    private QuestCanvas questCanvas;
    private static final Logger LOGGER = Logger.getLogger(ChapterEditorController.class.getName());
    private static final PseudoClass SELECTED_PSEUDO_CLASS = PseudoClass.getPseudoClass("selected");
    private Project project;
    private final TilePane questGrid = new TilePane(12, 12);
    private final Map<String, Button> questButtons = new HashMap<>();
    private Quest selectedQuest;

    @FXML
    public void initialize() {
        if (canvasHolder == null) {
            LOGGER.severe("canvasHolder not injected; check FXML id");
            return;
        }

        questCanvas = new QuestCanvas();
        questCanvas.setManaged(true);
        questCanvas.prefWidthProperty().bind(canvasHolder.widthProperty());
        questCanvas.prefHeightProperty().bind(canvasHolder.heightProperty());
        questGrid.setPadding(new Insets(24));
        questGrid.setPrefTileWidth(160);
        questGrid.setPrefTileHeight(96);
        questGrid.setHgap(12);
        questGrid.setVgap(12);
        questGrid.getStyleClass().add("quest-grid");
        canvasHolder.getChildren().setAll(questCanvas, questGrid);

        // apply initial settings
        var es = UserSettings.get();
        questCanvas.setShowGrid(es.showGrid);
        questCanvas.setSmoothPanning(es.smoothPanning);

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
    }

    // Example method to pan programmatically (no zoom exposed)
    public void panTo(double x, double y) {
        if (questCanvas != null) questCanvas.panTo(x, y);
    }

    // Exposed for SettingsController to refresh grid toggle if needed
    public void setShowGrid(boolean v) {
        if (questCanvas != null) questCanvas.setShowGrid(v);
    }

    public void setProject(Project project) {
        this.project = project;
        if (project != null) {
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
        if (project == null) {
            chapterListView.getItems().clear();
            displayChapter(null);
            return;
        }

        List<Chapter> chapters = project.getChapters();
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

        chapterListView.getItems().setAll(filtered);
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
        updateChapterTitle(chapter);
        questButtons.clear();
        questGrid.getChildren().clear();
        selectedQuest = null;
        clearQuestDetails();

        if (chapter == null) {
            return;
        }

        List<Quest> quests = chapter.quests();
        for (Quest quest : quests) {
            Button questButton = createQuestButton(quest);
            questButtons.put(quest.id(), questButton);
            questGrid.getChildren().add(questButton);
        }

        if (!quests.isEmpty()) {
            selectQuest(quests.get(0));
        }
    }

    private Button createQuestButton(Quest quest) {
        Button button = new Button(quest.title());
        button.getStyleClass().add("quest-node-button");
        button.setWrapText(true);
        button.setPrefWidth(160);
        button.setPrefHeight(96);
        button.setOnMouseClicked(event -> {
            if (event.getButton() != MouseButton.PRIMARY) {
                return;
            }
            selectQuest(quest);
            if (event.getClickCount() >= 2) {
                openQuestEditor(quest);
            }
        });
        return button;
    }

    private void selectQuest(Quest quest) {
        if (quest == null) {
            questButtons.values().forEach(button -> button.pseudoClassStateChanged(SELECTED_PSEUDO_CLASS, false));
            selectedQuest = null;
            clearQuestDetails();
            return;
        }

        selectedQuest = quest;
        questButtons.forEach((questId, button) ->
                button.pseudoClassStateChanged(SELECTED_PSEUDO_CLASS, questId.equals(quest.id())));
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
            Window owner = canvasHolder != null && canvasHolder.getScene() != null
                    ? canvasHolder.getScene().getWindow()
                    : null;
            if (owner != null) {
                stage.initOwner(owner);
            }
            stage.show();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to open quest editor for quest " + quest.id(), e);
        }
    }

    private void updateChapterTitle(Chapter chapter) {
        if (chapterTitleLabel != null) {
            chapterTitleLabel.setText(chapter != null ? chapter.title() : "");
        }
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

            Scene mainScene = canvasHolder.getScene();
            if (controller != null && mainScene != null) {
                controller.setScene(mainScene);
            }

            Stage dialog = new Stage();
            dialog.setTitle("Settings");
            dialog.initModality(Modality.APPLICATION_MODAL);
            Window owner = canvasHolder.getScene() != null ? canvasHolder.getScene().getWindow() : null;
            if (owner != null) {
                dialog.initOwner(owner);
            }

            Scene dialogScene = new Scene(root);
            ThemeService.apply(dialogScene, UserSettings.get().darkTheme);

            CheckBox showGrid = (CheckBox) root.lookup("#chkShowGrid");
            if (showGrid != null) {
                showGrid.selectedProperty().addListener((obs, oldVal, newVal) -> {
                    if (questCanvas != null) {
                        questCanvas.setShowGrid(Boolean.TRUE.equals(newVal));
                    }
                });
            }
            CheckBox smooth = (CheckBox) root.lookup("#chkSmoothPanning");
            if (smooth != null) {
                smooth.selectedProperty().addListener((obs, oldVal, newVal) -> {
                    if (questCanvas != null) {
                        questCanvas.setSmoothPanning(Boolean.TRUE.equals(newVal));
                    }
                });
            }
            CheckBox dark = (CheckBox) root.lookup("#chkDarkTheme");
            if (dark != null) {
                dark.selectedProperty().addListener((obs, oldVal, newVal) -> ThemeService.apply(dialogScene, Boolean.TRUE.equals(newVal)));
            }

            dialog.setScene(dialogScene);
            dialog.showAndWait();

            var es = UserSettings.get();
            if (questCanvas != null) {
                questCanvas.setShowGrid(es.showGrid);
                questCanvas.setSmoothPanning(es.smoothPanning);
            }
        } catch (Exception ignored) {
        }
    }
}
