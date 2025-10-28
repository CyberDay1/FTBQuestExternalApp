package dev.ftbq.editor.ui.dialogs;

import dev.ftbq.editor.ThemeService;
import javafx.scene.control.DialogPane;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;

/**
 * Simple dialog that captures optional refinement instructions for the AI generator.
 */
public class AiPromptDialog extends TextInputDialog {

    public AiPromptDialog() {
        setTitle("Generate Quest Chapter");
        setHeaderText("Refine the AI request");
        setContentText("Describe the tone, goals, or constraints for this chapter:");

        DialogPane dialogPane = getDialogPane();
        dialogPane.getStyleClass().add("ai-prompt-dialog");

        dialogPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                return;
            }
            newScene.windowProperty().addListener((windowObs, oldWindow, newWindow) -> {
                if (newWindow instanceof Stage stage) {
                    ThemeService.getInstance().registerStage(stage);
                }
            });
        });
    }
}
