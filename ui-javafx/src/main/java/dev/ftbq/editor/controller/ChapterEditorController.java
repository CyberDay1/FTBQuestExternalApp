package dev.ftbq.editor.controller;

import dev.ftbq.editor.service.ThemeService;
import dev.ftbq.editor.service.UserSettings;
import dev.ftbq.editor.ui.graph.QuestCanvas;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ChapterEditorController {
    @FXML private StackPane canvasHolder;
    private QuestCanvas questCanvas;
    private static final Logger LOGGER = Logger.getLogger(ChapterEditorController.class.getName());

    @FXML
    public void initialize() {
        questCanvas = new QuestCanvas();
        questCanvas.setManaged(true);
        questCanvas.prefWidthProperty().bind(canvasHolder.widthProperty());
        questCanvas.prefHeightProperty().bind(canvasHolder.heightProperty());
        canvasHolder.getChildren().setAll(questCanvas);

        // apply initial settings
        var es = UserSettings.get();
        questCanvas.setShowGrid(es.showGrid);
        questCanvas.setSmoothPanning(es.smoothPanning);
    }

    // Example method to pan programmatically (no zoom exposed)
    public void panTo(double x, double y) {
        if (questCanvas != null) questCanvas.panTo(x, y);
    }

    // Exposed for SettingsController to refresh grid toggle if needed
    public void setShowGrid(boolean v) {
        if (questCanvas != null) questCanvas.setShowGrid(v);
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
