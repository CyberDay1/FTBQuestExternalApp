package dev.ftbq.editor;

import dev.ftbq.editor.controller.ChapterEditorController;
import dev.ftbq.editor.controller.ImportSnbtDialog;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.importer.snbt.model.QuestImportResult;
import dev.ftbq.editor.importer.snbt.model.QuestImportSummary;
import dev.ftbq.editor.importer.snbt.validation.SnbtValidationReport;
import dev.ftbq.editor.importer.snbt.validation.SnbtValidationService;
import dev.ftbq.editor.io.snbt.SnbtQuestMapper;
import dev.ftbq.editor.services.UiServiceLocator;
import dev.ftbq.editor.services.bus.ServiceLocator;
import dev.ftbq.editor.services.io.SnbtImportExportService;
import dev.ftbq.editor.services.logging.StructuredLogger;
import dev.ftbq.editor.ui.AiQuestCreationTab;
import dev.ftbq.editor.validation.ValidationIssue;
import dev.ftbq.editor.view.ChapterGroupBrowserController;
import dev.ftbq.editor.view.graph.layout.JsonQuestLayoutStore;
import dev.ftbq.editor.view.graph.layout.QuestLayoutStore;
import dev.ftbq.editor.viewmodel.ChapterEditorViewModel;
import dev.ftbq.editor.viewmodel.ChapterGroupBrowserViewModel;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MainApp extends Application {
    private final StructuredLogger logger = ServiceLocator.loggerFactory().create(MainApp.class);
    private final SnbtImportExportService importExportService = new SnbtImportExportService();
    private final SnbtValidationService validationService = new SnbtValidationService();
    private final SnbtQuestMapper snbtQuestMapper = new SnbtQuestMapper();
    private ChapterGroupBrowserViewModel chapterGroupBrowserViewModel;
    private ChapterEditorViewModel chapterEditorViewModel;
    private QuestFile currentQuestFile;
    private Path workspace;
    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) throws IOException {
        this.primaryStage = primaryStage;
        logger.info("Starting FTB Quest Editor UI");
        ensureLayoutStore();
        FXMLLoader chapterLoader = new FXMLLoader(getClass().getResource("/dev/ftbq/editor/view/chapter_group_browser.fxml"));
        Parent chapterRoot = chapterLoader.load();

        ChapterGroupBrowserController chapterController = chapterLoader.getController();
        chapterGroupBrowserViewModel = new ChapterGroupBrowserViewModel();
        chapterController.setViewModel(chapterGroupBrowserViewModel);

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab chapterTab = new Tab("Chapter Groups");
        chapterTab.setContent(chapterRoot);
        chapterTab.setClosable(false);

        tabPane.getTabs().add(chapterTab);

        FXMLLoader chapterEditorLoader = new FXMLLoader(getClass().getResource("/dev/ftbq/editor/view/chapter_editor.fxml"));
        Parent chapterEditorRoot = chapterEditorLoader.load();
        ChapterEditorController chapterEditorController = chapterEditorLoader.getController();
        chapterEditorViewModel = new ChapterEditorViewModel();
        chapterEditorController.setViewModel(chapterEditorViewModel);
        chapterEditorViewModel.loadSampleChapters();
        currentQuestFile = QuestFile.builder()
                .id("ftbquests:editor")
                .title("Quest Editor Pack")
                .chapters(new ArrayList<>(chapterEditorViewModel.getChapters()))
                .chapterGroups(List.of())
                .lootTables(List.of())
                .build();
        chapterGroupBrowserViewModel.loadFromQuestFile(currentQuestFile);
        Tab chapterEditorTab = new Tab("Chapter Editor", chapterEditorRoot);
        chapterEditorTab.setClosable(false);
        tabPane.getTabs().add(chapterEditorTab);

        FXMLLoader lootLoader = new FXMLLoader(getClass().getResource("/dev/ftbq/editor/view/loot_table_editor.fxml"));
        Parent lootRoot = lootLoader.load();
        Tab lootTab = new Tab("Loot Tables", lootRoot);
        lootTab.setClosable(false);
        tabPane.getTabs().add(lootTab);

        AiQuestCreationTab aiQuestCreationTab = new AiQuestCreationTab();
        aiQuestCreationTab.setQuestFileSupplier(() -> currentQuestFile);
        aiQuestCreationTab.setWorkspaceRoot(workspace);
        tabPane.getTabs().add(aiQuestCreationTab);

        // Quest editor removed â€” quests are now edited via Chapter Editor directly
        try {
            logger.info("Quest editor deprecated â€” skipping quest_editor.fxml load");
        } catch (Exception e) {
            logger.warn("Quest editor load skipped", e);
        }

        try {
            FXMLLoader settingsLoader = new FXMLLoader(getClass().getResource("/dev/ftbq/editor/view/settings.fxml"));
            Parent settingsRoot = settingsLoader.load();
            Tab settingsTab = new Tab("Settings", settingsRoot);
            settingsTab.setClosable(false);
            tabPane.getTabs().add(settingsTab);
        } catch (Exception e) {
            logger.error("Failed to load settings UI", e);
        }

        MenuBar menuBar = createMenuBar(primaryStage);
        BorderPane root = new BorderPane();
        root.setTop(menuBar);
        root.setCenter(tabPane);

        Scene scene = new Scene(root);

        ThemeService themeService = ThemeService.getInstance();
        themeService.registerStage(primaryStage);

        primaryStage.setTitle("FTB Quest Editor");
        primaryStage.setScene(scene);
        primaryStage.setWidth(960);
        primaryStage.setHeight(720);
        primaryStage.show();
        logger.info("Primary stage shown", StructuredLogger.field("width", primaryStage.getWidth()), StructuredLogger.field("height", primaryStage.getHeight()));
    }

    public static void main(String[] args) {
        HeadlessLauncher.main(args);
    }

    @Override
    public void stop() throws Exception {
        QuestLayoutStore layoutStore = UiServiceLocator.questLayoutStore;
        if (layoutStore != null) {
            layoutStore.flush();
        }
        logger.info("Stopping FTB Quest Editor UI");
        super.stop();
    }

    private void ensureLayoutStore() {
        if (workspace == null) {
            String workspaceProperty = System.getProperty("ftbq.editor.workspace", ".");
            workspace = Path.of(workspaceProperty).toAbsolutePath().normalize();
        }
        if (UiServiceLocator.questLayoutStore == null) {
            UiServiceLocator.questLayoutStore = new JsonQuestLayoutStore(workspace);
        }
    }

    private MenuBar createMenuBar(Stage owner) {
        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("File");
        MenuItem importItem = new MenuItem("Import Quests from SNBT...");
        importItem.setOnAction(event -> showImportDialog(owner));
        fileMenu.getItems().add(importItem);
        Menu toolsMenu = new Menu("Tools");
        MenuItem validateItem = new MenuItem("Validate Quest Pack");
        validateItem.setOnAction(event -> validateCurrentPack());
        toolsMenu.getItems().add(validateItem);
        menuBar.getMenus().addAll(fileMenu, toolsMenu);
        return menuBar;
    }

    private void showImportDialog(Stage owner) {
        ImportSnbtDialog dialog = new ImportSnbtDialog(owner, importExportService, currentQuestFile, workspace);
        dialog.showAndWait().ifPresent(this::applyImportResult);
    }

    private void validateCurrentPack() {
        Alert alert;
        try {
            String snbt = snbtQuestMapper.toSnbt(currentQuestFile);
            SnbtValidationReport report = validationService.validate(snbt);
            if (report.issues().isEmpty()) {
                alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Validation complete");
                alert.setHeaderText("No issues detected");
                alert.setContentText("The quest pack conforms to the supported SNBT schema.");
            } else {
                Alert.AlertType type = report.errors().isEmpty() ? Alert.AlertType.WARNING : Alert.AlertType.ERROR;
                alert = new Alert(type);
                alert.setTitle("Validation issues found");
                alert.setHeaderText(report.errors().isEmpty() ? "Warnings detected" : "Errors detected");
                StringBuilder builder = new StringBuilder();
                for (ValidationIssue issue : report.issues()) {
                    builder.append(issue.severity())
                            .append(' ')
                            .append(issue.path())
                            .append(" - ")
                            .append(issue.message())
                            .append('\n');
                }
                TextArea details = new TextArea(builder.toString());
                details.setEditable(false);
                details.setWrapText(false);
                details.setPrefColumnCount(80);
                details.setPrefRowCount(Math.min(10, report.issues().size() + 1));
                alert.getDialogPane().setContent(details);
            }
        } catch (Exception ex) {
            logger.error("Validation failed", ex);
            alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Validation failed");
            alert.setHeaderText("Unable to validate quest pack");
            alert.setContentText(ex.getMessage());
        }
        if (primaryStage != null) {
            alert.initOwner(primaryStage);
        }
        alert.showAndWait();
    }

    private void applyImportResult(QuestImportResult result) {
        if (result == null) {
            return;
        }
        currentQuestFile = result.questFile();
        updateViewModelsFromQuestFile();
        persistQuestFile();
        showImportSummary(result.summary());
    }

    private void updateViewModelsFromQuestFile() {
        chapterEditorViewModel.loadFromQuestFile(currentQuestFile);
        chapterGroupBrowserViewModel.loadFromQuestFile(currentQuestFile);
    }

    private void persistQuestFile() {
        try {
            importExportService.exportPack(currentQuestFile, workspace.toFile());
        } catch (Exception ex) {
            logger.error("Failed to write quest SNBT", ex);
            showError("Failed to write quest data", ex.getMessage());
        }
    }

    private void showImportSummary(QuestImportSummary summary) {
        if (summary == null) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Added chapters: ").append(summary.addedChapters().size()).append('\n');
        if (!summary.addedChapters().isEmpty()) {
            builder.append("  ").append(String.join(", ", summary.addedChapters())).append('\n');
        }
        builder.append("Merged chapters: ").append(summary.mergedChapters().size()).append('\n');
        builder.append("Added quests: ").append(summary.addedQuests().size()).append('\n');
        if (!summary.renamedIds().isEmpty()) {
            builder.append("Renamed IDs: ").append(String.join(", ", summary.renamedIds())).append('\n');
        }
        List<String> warnings = new ArrayList<>();
        if (summary.warnings() != null) {
            warnings.addAll(summary.warnings());
        }
        if (summary.assetWarnings() != null) {
            warnings.addAll(summary.assetWarnings());
        }
        if (!warnings.isEmpty()) {
            builder.append('\n').append("Warnings:").append('\n');
            for (String warning : warnings) {
                builder.append(" - ").append(warning).append('\n');
            }
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        if (primaryStage != null) {
            alert.initOwner(primaryStage);
        }
        alert.setTitle("Import complete");
        alert.setHeaderText("Quest import summary");
        alert.setContentText(builder.toString());
        alert.showAndWait();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        if (primaryStage != null) {
            alert.initOwner(primaryStage);
        }
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message == null ? "An unknown error occurred." : message);
        alert.showAndWait();
    }
}




