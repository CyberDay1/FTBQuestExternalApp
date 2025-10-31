package dev.ftbq.editor.controller;

import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.service.ThemeService;
import dev.ftbq.editor.service.UserSettings;
import dev.ftbq.editor.store.Project;
import dev.ftbq.editor.ui.graph.QuestCanvas;
import dev.ftbq.editor.view.QuestEditorController;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChapterEditorController {
    @FXML private StackPane canvasHolder;
    @FXML private ListView<Chapter> chapterListView;
    @FXML private Label chapterTitleLabel;
    private QuestCanvas questCanvas;
    private static final Logger LOGGER = Logger.getLogger(ChapterEditorController.class.getName());
    private Project project;

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
        canvasHolder.getChildren().setAll(questCanvas);

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
            chapterListView.getSelectionModel().selectedItemProperty().addListener((obs, oldChapter, newChapter) ->
                    updateChapterTitle(newChapter));
        }
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
        } else if (chapterListView != null) {
            chapterListView.getItems().clear();
            updateChapterTitle(null);
        }
    }

    private void refreshChapterList() {
        if (chapterListView == null || project == null) {
            return;
        }
        chapterListView.getItems().clear();
        chapterListView.getItems().addAll(project.getChapters());
        chapterListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && chapterListView.getSelectionModel().getSelectedItem() != null) {
                openQuestEditor(chapterListView.getSelectionModel().getSelectedItem());
            }
        });
        if (!chapterListView.getItems().isEmpty()) {
            chapterListView.getSelectionModel().selectFirst();
        } else {
            updateChapterTitle(null);
        }
    }

    private void openQuestEditor(Chapter chapter) {
        if (chapter == null) {
            return;
        }
        if (chapter.quests().isEmpty()) {
            LOGGER.log(Level.INFO, "Chapter {0} has no quests to edit", chapter.id());
            return;
        }
        Quest quest = chapter.quests().get(0);
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
            LOGGER.log(Level.WARNING, "Failed to open quest editor for chapter " + chapter.id(), e);
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
