package dev.ftbq.editor;

import dev.ftbq.editor.ThemeService;
import dev.ftbq.editor.app.ProjectFileHandler;
import dev.ftbq.editor.app.ProjectFileHandler.ProjectData;
import dev.ftbq.editor.app.VanillaItemDatabase;
import dev.ftbq.editor.controller.ChapterEditorController;
import dev.ftbq.editor.controller.ImportSnbtDialog;
import dev.ftbq.editor.controller.SettingsController;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.importer.snbt.model.QuestImportResult;
import dev.ftbq.editor.importer.snbt.model.QuestImportSummary;
import dev.ftbq.editor.importer.snbt.validation.SnbtValidationReport;
import dev.ftbq.editor.importer.snbt.validation.SnbtValidationService;
import dev.ftbq.editor.io.snbt.SnbtQuestMapper;
import dev.ftbq.editor.support.UiServiceLocator;
import dev.ftbq.editor.services.bus.ServiceLocator;
import dev.ftbq.editor.services.io.SnbtImportExportService;
import dev.ftbq.editor.services.logging.StructuredLogger;
import dev.ftbq.editor.store.StoreDao;
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
import javafx.scene.control.ButtonType;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MainApp extends Application implements SettingsController.AutosaveSettings {
    private final StructuredLogger logger = ServiceLocator.loggerFactory().create(MainApp.class);
    private final SnbtImportExportService importExportService = new SnbtImportExportService();
    private final SnbtValidationService validationService = new SnbtValidationService();
    private final SnbtQuestMapper snbtQuestMapper = new SnbtQuestMapper();
    private final StoreDao storeDao = UiServiceLocator.getStoreDao();
    private final ProjectFileHandler projectFileHandler = new ProjectFileHandler(storeDao);
    private final VanillaItemDatabase vanillaItemDatabase = new VanillaItemDatabase(storeDao);
    private ScheduledExecutorService autosaveExecutor;
    private int autosaveIntervalMinutes = 1;
    private Path autosaveFile;
    private static final String AUTOSAVE_SETTING_KEY = "autosave.minutes";
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
        loadAutosavePreferences();
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
        setCurrentQuestFile(QuestFile.builder()
                .id("ftbquests:editor")
                .title("Quest Editor Pack")
                .chapters(new ArrayList<>(chapterEditorViewModel.getChapters()))
                .chapterGroups(List.of())
                .lootTables(List.of())
                .build());
        chapterGroupBrowserViewModel.loadFromQuestFile(getCurrentQuestFile());
        Tab chapterEditorTab = new Tab("Chapter Editor", chapterEditorRoot);
        chapterEditorTab.setClosable(false);
        tabPane.getTabs().add(chapterEditorTab);

        FXMLLoader lootLoader = new FXMLLoader(getClass().getResource("/dev/ftbq/editor/view/loot_table_editor.fxml"));
        Parent lootRoot = lootLoader.load();
        Tab lootTab = new Tab("Loot Tables", lootRoot);
        lootTab.setClosable(false);
        tabPane.getTabs().add(lootTab);

        AiQuestCreationTab aiQuestCreationTab = new AiQuestCreationTab();
        aiQuestCreationTab.setQuestFileSupplier(this::getCurrentQuestFile);
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
            SettingsController settingsController = settingsLoader.getController();
            if (settingsController != null) {
                settingsController.setAutosaveSettings(this);
            }
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
        recoverAutosaveIfPresent();
        startAutosaveScheduler();
        primaryStage.show();
        logger.info("Primary stage shown", StructuredLogger.field("width", primaryStage.getWidth()), StructuredLogger.field("height", primaryStage.getHeight()));
    }

    public static void main(String[] args) {
        HeadlessLauncher.main(args);
    }

    @Override
    public void stop() throws Exception {
        QuestLayoutStore layoutStore = dev.ftbq.editor.services.UiServiceLocator.questLayoutStore;
        if (layoutStore != null) {
            layoutStore.flush();
        }
        stopAutosaveScheduler();
        logger.info("Stopping FTB Quest Editor UI");
        super.stop();
    }

    private void ensureLayoutStore() {
        if (workspace == null) {
            String workspaceProperty = System.getProperty("ftbq.editor.workspace", ".");
            workspace = Path.of(workspaceProperty).toAbsolutePath().normalize();
            autosaveFile = workspace.resolve("autosave.ftbq");
        }
        if (dev.ftbq.editor.services.UiServiceLocator.questLayoutStore == null) {
            dev.ftbq.editor.services.UiServiceLocator.questLayoutStore = new JsonQuestLayoutStore(workspace);
        }
    }

    private MenuBar createMenuBar(Stage owner) {
        MenuBar menuBar = new MenuBar();
        Menu fileMenu = new Menu("File");
        MenuItem importItem = new MenuItem("Import Quests from SNBT...");
        importItem.setOnAction(event -> showImportDialog(owner));
        MenuItem loadProjectItem = new MenuItem("Load Project...");
        loadProjectItem.setOnAction(event -> loadProject(owner));
        MenuItem saveProjectItem = new MenuItem("Save Project As...");
        saveProjectItem.setOnAction(event -> saveProjectAs(owner));
        MenuItem exportItem = new MenuItem("Export Quest Pack...");
        exportItem.setOnAction(event -> exportQuestPack(owner));
        fileMenu.getItems().addAll(importItem, loadProjectItem, saveProjectItem, new SeparatorMenuItem(), exportItem);
        Menu toolsMenu = new Menu("Tools");
        MenuItem validateItem = new MenuItem("Validate Quest Pack");
        validateItem.setOnAction(event -> validateCurrentPack());
        MenuItem saveItemsItem = new MenuItem("Save Imported Items");
        saveItemsItem.setOnAction(event -> saveImportedItems(owner));
        toolsMenu.getItems().addAll(validateItem, saveItemsItem);
        menuBar.getMenus().addAll(fileMenu, toolsMenu);
        return menuBar;
    }

    private void showImportDialog(Stage owner) {
        ImportSnbtDialog dialog = new ImportSnbtDialog(owner, importExportService, getCurrentQuestFile(), workspace);
        dialog.showAndWait().ifPresent(this::applyImportResult);
    }

    private void loadProject(Stage owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load Project");
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("FTBQ Project (*.ftbq)", "*.ftbq"));
        File file = chooser.showOpenDialog(owner);
        if (file == null) {
            return;
        }
        Path path = file.toPath();
        try {
            ProjectData data = projectFileHandler.loadProject(path);
            setCurrentQuestFile(data.questFile());
            updateViewModelsFromQuestFile();
            UiServiceLocator.rebuildVersionCatalog();
            logger.info("Project loaded", StructuredLogger.field("path", path.toString()));
            restartAutosaveScheduler();
        } catch (Exception ex) {
            logger.error("Failed to load project", ex);
            showError("Failed to load project", ex.getMessage());
        }
    }

    private void saveProjectAs(Stage owner) {
        QuestFile questFile = getCurrentQuestFile();
        if (questFile == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Project");
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("FTBQ Project (*.ftbq)", "*.ftbq"));
        File file = chooser.showSaveDialog(owner);
        if (file == null) {
            return;
        }
        Path path = ensureExtension(file.toPath(), ".ftbq");
        try {
            projectFileHandler.saveProject(path, questFile);
            logger.info("Project saved", StructuredLogger.field("path", path.toString()));
        } catch (Exception ex) {
            logger.error("Failed to save project", ex);
            showError("Failed to save project", ex.getMessage());
        }
    }

    private void exportQuestPack(Stage owner) {
        QuestFile questFile = getCurrentQuestFile();
        if (questFile == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Quest Pack");
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("Quest Pack (*.zip)", "*.zip"));
        File file = chooser.showSaveDialog(owner);
        if (file == null) {
            return;
        }
        Path path = ensureExtension(file.toPath(), ".zip");
        try {
            projectFileHandler.exportQuestPack(path, questFile);
            logger.info("Quest pack exported", StructuredLogger.field("path", path.toString()));
        } catch (Exception ex) {
            logger.error("Failed to export quest pack", ex);
            showError("Failed to export quest pack", ex.getMessage());
        }
    }

    private void saveImportedItems(Stage owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Imported Items");
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
        File file = chooser.showSaveDialog(owner);
        if (file == null) {
            return;
        }
        Path path = ensureExtension(file.toPath(), ".json");
        try {
            vanillaItemDatabase.saveAll(path);
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            if (primaryStage != null) {
                alert.initOwner(primaryStage);
            }
            alert.setTitle("Items saved");
            alert.setHeaderText("Imported items exported");
            alert.setContentText("Saved imported items to " + path);
            alert.showAndWait();
            logger.info("Imported items saved", StructuredLogger.field("path", path.toString()));
        } catch (Exception ex) {
            logger.error("Failed to save imported items", ex);
            showError("Failed to save imported items", ex.getMessage());
        }
    }

    private void validateCurrentPack() {
        Alert alert;
        try {
            QuestFile questFile = getCurrentQuestFile();
            if (questFile == null) {
                showError("No quest data", "There is no quest file loaded to validate.");
                return;
            }
            String snbt = snbtQuestMapper.toSnbt(questFile);
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
        setCurrentQuestFile(result.questFile());
        updateViewModelsFromQuestFile();
        persistQuestFile();
        showImportSummary(result.summary());
        restartAutosaveScheduler();
    }

    private void updateViewModelsFromQuestFile() {
        QuestFile questFile = getCurrentQuestFile();
        if (questFile == null) {
            return;
        }
        chapterEditorViewModel.loadFromQuestFile(questFile);
        chapterGroupBrowserViewModel.loadFromQuestFile(questFile);
    }

    private void persistQuestFile() {
        QuestFile questFile = getCurrentQuestFile();
        if (questFile == null || workspace == null) {
            return;
        }
        try {
            importExportService.exportPack(questFile, workspace.toFile());
        } catch (Exception ex) {
            logger.error("Failed to write quest SNBT", ex);
            showError("Failed to write quest data", ex.getMessage());
        }
        try {
            Path projectPath = workspace.resolve("current.ftbq");
            projectFileHandler.saveProject(projectPath, questFile);
        } catch (Exception ex) {
            logger.warn("Failed to write project file", ex);
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

    private void loadAutosavePreferences() {
        storeDao.getSetting(AUTOSAVE_SETTING_KEY).ifPresent(value -> {
            try {
                int parsed = Integer.parseInt(value.trim());
                if (parsed >= 1) {
                    autosaveIntervalMinutes = parsed;
                }
            } catch (NumberFormatException ex) {
                logger.warn("Invalid autosave interval in settings", ex, StructuredLogger.field("value", value));
            }
        });
    }

    private void recoverAutosaveIfPresent() {
        Path target = autosaveFile;
        if (target == null || !Files.exists(target)) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Autosave available");
        alert.setHeaderText("Recover autosave?");
        alert.setContentText("An autosave was found from a previous session. Would you like to restore it?");
        ButtonType recover = new ButtonType("Recover");
        ButtonType discard = new ButtonType("Discard");
        alert.getButtonTypes().setAll(recover, discard);
        if (primaryStage != null) {
            alert.initOwner(primaryStage);
        }
        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == recover) {
            try {
                ProjectData data = projectFileHandler.loadProject(target);
                setCurrentQuestFile(data.questFile());
                updateViewModelsFromQuestFile();
                UiServiceLocator.rebuildVersionCatalog();
                restartAutosaveScheduler();
                logger.info("Autosave restored", StructuredLogger.field("path", target.toString()));
            } catch (Exception ex) {
                logger.error("Autosave recovery failed", ex);
                showError("Failed to recover autosave", ex.getMessage());
            }
        }
        try {
            Files.deleteIfExists(target);
        } catch (IOException ex) {
            logger.warn("Failed to delete autosave file", ex, StructuredLogger.field("path", target.toString()));
        }
    }

    private void startAutosaveScheduler() {
        synchronized (this) {
            stopAutosaveSchedulerInternal();
            if (autosaveIntervalMinutes < 1) {
                autosaveIntervalMinutes = 1;
            }
            autosaveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "ftbq-autosave");
                thread.setDaemon(true);
                return thread;
            });
            autosaveExecutor.scheduleAtFixedRate(this::performAutosave, autosaveIntervalMinutes, autosaveIntervalMinutes, TimeUnit.MINUTES);
        }
    }

    private void restartAutosaveScheduler() {
        startAutosaveScheduler();
    }

    private void stopAutosaveScheduler() {
        synchronized (this) {
            stopAutosaveSchedulerInternal();
        }
    }

    private void stopAutosaveSchedulerInternal() {
        if (autosaveExecutor != null) {
            autosaveExecutor.shutdownNow();
        }
        autosaveExecutor = null;
    }

    private void performAutosave() {
        QuestFile questFile = getCurrentQuestFile();
        Path target = autosaveFile;
        if (questFile == null || target == null) {
            return;
        }
        try {
            projectFileHandler.saveProject(target, questFile);
            logger.debug("Autosave completed", StructuredLogger.field("path", target.toString()));
        } catch (Exception ex) {
            logger.warn("Autosave failed", ex, StructuredLogger.field("path", target.toString()));
        }
    }

    private Path ensureExtension(Path path, String extension) {
        String fileName = path.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(extension)) {
            return path.resolveSibling(fileName + extension);
        }
        return path;
    }

    private synchronized QuestFile getCurrentQuestFile() {
        return currentQuestFile;
    }

    private synchronized void setCurrentQuestFile(QuestFile questFile) {
        this.currentQuestFile = Objects.requireNonNull(questFile, "questFile");
    }

    @Override
    public synchronized int getIntervalMinutes() {
        return autosaveIntervalMinutes;
    }

    @Override
    public void updateIntervalMinutes(int minutes) {
        if (minutes < 1) {
            throw new IllegalArgumentException("Autosave interval must be at least one minute");
        }
        synchronized (this) {
            autosaveIntervalMinutes = minutes;
        }
        storeDao.setSetting(AUTOSAVE_SETTING_KEY, Integer.toString(minutes));
        restartAutosaveScheduler();
    }
}




