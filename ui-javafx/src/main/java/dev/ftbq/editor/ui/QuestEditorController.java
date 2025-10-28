package dev.ftbq.editor.ui;

import dev.ftbq.editor.ThemeService;
import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.io.snbt.AiQuestBridge;
import dev.ftbq.editor.services.ai.QuestGenerationService;
import dev.ftbq.editor.services.generator.ModIntent;
import dev.ftbq.editor.services.generator.QuestDesignSpec;
import dev.ftbq.editor.ui.dialogs.AiPromptDialog;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Controller responsible for surfacing the AI quest generation workflow.
 */
public class QuestEditorController {

    private static final String DEFAULT_PROMPT_PREFIX = "User prompt:";

    @FXML
    private ToolBar topToolbar;

    @FXML
    private TreeView<String> questTreeView;

    private final QuestGenerationService questGenerationService;
    private final AiQuestBridge aiQuestBridge;
    private Supplier<QuestDesignSpec> designSpecSupplier;
    private Supplier<ModIntent> modIntentSupplier;
    private Button saveDraftButton;
    private Path workspaceRoot;

    public QuestEditorController() {
        this(new QuestGenerationService(), new AiQuestBridge());
    }

    QuestEditorController(QuestGenerationService questGenerationService, AiQuestBridge aiQuestBridge) {
        this.questGenerationService = Objects.requireNonNull(questGenerationService, "questGenerationService");
        this.aiQuestBridge = Objects.requireNonNull(aiQuestBridge, "aiQuestBridge");
    }

    @FXML
    private void initialize() {
        if (topToolbar != null) {
            Button generateViaAiButton = new Button("Generate via AI");
            generateViaAiButton.getStyleClass().add("accent-button");
            generateViaAiButton.setOnAction(event -> onGenerateViaAi());
            topToolbar.getItems().add(0, generateViaAiButton);

            saveDraftButton = new Button("Save to Drafts");
            saveDraftButton.setDisable(true);
            saveDraftButton.setOnAction(event -> onSaveDraft());
            topToolbar.getItems().add(saveDraftButton);
        }

        if (questTreeView != null) {
            questTreeView.setShowRoot(false);
        }
    }

    public void setDesignSpecSupplier(Supplier<QuestDesignSpec> designSpecSupplier) {
        this.designSpecSupplier = designSpecSupplier;
    }

    public void setModIntentSupplier(Supplier<ModIntent> modIntentSupplier) {
        this.modIntentSupplier = modIntentSupplier;
    }

    public void setWorkspaceRoot(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    private void onGenerateViaAi() {
        QuestDesignSpec designSpec = resolveDesignSpec();
        ModIntent modIntent = resolveModIntent();

        if (designSpec == null || modIntent == null) {
            showError("Generation unavailable", "Design specification or mod intent is not configured.");
            return;
        }

        AiPromptDialog dialog = new AiPromptDialog();
        Window owner = findWindow();
        if (owner != null) {
            dialog.initOwner(owner);
        }

        Optional<String> promptResult = dialog.showAndWait();
        if (promptResult.isEmpty()) {
            return;
        }

        String userPrompt = promptResult.map(String::trim).orElse("");
        ModIntent effectiveIntent = appendPrompt(modIntent, userPrompt);

        generateChapter(designSpec, effectiveIntent);
    }

    @SuppressWarnings("removal")
    private void generateChapter(QuestDesignSpec designSpec, ModIntent modIntent) {
        try {
            String snbt = questGenerationService.generateQuestChapter(designSpec, modIntent);
            QuestFile questFile = aiQuestBridge.parse(snbt);
            displayQuestTree(questFile);
            if (saveDraftButton != null) {
                saveDraftButton.setDisable(false);
            }
        } catch (Exception ex) {
            showError("AI generation failed", ex.getMessage());
        }
    }

    private void onSaveDraft() {
        Path root = workspaceRoot;
        if (root == null) {
            root = defaultWorkspace();
        }

        try {
            Path saved = aiQuestBridge.saveDraft(root);
            showInfo("Draft saved", "Draft written to " + saved.toAbsolutePath());
        } catch (IOException ioException) {
            showError("Failed to save draft", ioException.getMessage());
        } catch (IllegalStateException illegalStateException) {
            showError("Nothing to save", illegalStateException.getMessage());
        }
    }

    private void displayQuestTree(QuestFile questFile) {
        if (questTreeView == null) {
            return;
        }

        TreeItem<String> rootItem = new TreeItem<>(questFile.title());
        for (Chapter chapter : questFile.chapters()) {
            TreeItem<String> chapterItem = new TreeItem<>(chapter.title());
            for (Quest quest : chapter.quests()) {
                chapterItem.getChildren().add(new TreeItem<>(quest.title()));
            }
            rootItem.getChildren().add(chapterItem);
        }

        questTreeView.setShowRoot(true);
        questTreeView.setRoot(rootItem);
        rootItem.setExpanded(true);
        rootItem.getChildren().forEach(child -> child.setExpanded(true));
    }

    private QuestDesignSpec resolveDesignSpec() {
        if (designSpecSupplier != null) {
            return designSpecSupplier.get();
        }
        return defaultDesignSpec();
    }

    private ModIntent resolveModIntent() {
        if (modIntentSupplier != null) {
            return modIntentSupplier.get();
        }
        return defaultModIntent();
    }

    private QuestDesignSpec defaultDesignSpec() {
        return new QuestDesignSpec(
                "getting started",
                List.of("Intro", "Progress", "Challenge"),
                List.of("Unlock basic tools", "Introduce automation"),
                List.of("Exploration", "Technology"),
                Set.of(),
                List.of("collect", "craft", "kill"),
                8,
                200
        );
    }

    private ModIntent defaultModIntent() {
        return new ModIntent(
                "minecraft",
                List.of("Core gameplay"),
                "Base prompt",
                List.of()
        );
    }

    private ModIntent appendPrompt(ModIntent baseIntent, String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return baseIntent;
        }
        String existingNotes = baseIntent.progressionNotes();
        String combinedNotes = existingNotes.isBlank()
                ? prompt
                : existingNotes + System.lineSeparator() + DEFAULT_PROMPT_PREFIX + ' ' + prompt;
        return new ModIntent(
                baseIntent.modId(),
                baseIntent.features(),
                combinedNotes,
                baseIntent.exampleReferences()
        );
    }

    private void showError(String title, String message) {
        showAlert(Alert.AlertType.ERROR, title, message);
    }

    private void showInfo(String title, String message) {
        showAlert(Alert.AlertType.INFORMATION, title, message);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type, message, ButtonType.OK);
            alert.setTitle(title);
            alert.setHeaderText(null);
            Window owner = findWindow();
            if (owner != null) {
                alert.initOwner(owner);
            }
            alert.setOnShown(event -> {
                Window window = alert.getDialogPane().getScene().getWindow();
                if (window instanceof javafx.stage.Stage stage) {
                    ThemeService.getInstance().registerStage(stage);
                }
            });
            alert.showAndWait();
        });
    }

    private Window findWindow() {
        if (topToolbar != null && topToolbar.getScene() != null) {
            return topToolbar.getScene().getWindow();
        }
        if (questTreeView != null && questTreeView.getScene() != null) {
            return questTreeView.getScene().getWindow();
        }
        return null;
    }

    private Path defaultWorkspace() {
        return Paths.get(System.getProperty("ftbq.editor.workspace", "."))
                .toAbsolutePath()
                .normalize();
    }
}
