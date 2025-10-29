package dev.ftbq.editor;

import dev.ftbq.editor.ThemeService;
import dev.ftbq.editor.app.ProjectFileHandler;
import dev.ftbq.editor.app.ProjectFileHandler.ProjectData;
import dev.ftbq.editor.app.VanillaItemDatabase;
import dev.ftbq.editor.controller.ChapterEditorController;
import dev.ftbq.editor.controller.ImportSnbtDialog;
import dev.ftbq.editor.controller.MenuController;
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
import dev.ftbq.editor.service.AutosaveService;
import dev.ftbq.editor.service.QuestZipGenerator;
import dev.ftbq.editor.service.UserSettings;
import dev.ftbq.editor.service.UserSettings.EditorSettings;
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
import javafx.scene.control.MenuBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class MainApp extends Application implements SettingsController.AutosaveSettings {
    private final StructuredLogger logger = ServiceLocator.loggerFactory().create(MainApp.class);
    private final SnbtImportExportService importExportService = new SnbtImportExportService();
    private final SnbtValidationService validationService = new SnbtValidationService();
    private final SnbtQuestMapper snbtQuestMapper = new SnbtQuestMapper();
    private final StoreDao storeDao = UiServiceLocator.getStoreDao();
    private final ProjectFileHandler projectFileHandler = new ProjectFileHandler(storeDao);
    private final VanillaItemDatabase vanillaItemDatabase = new VanillaItemDatabase(storeDao);
    private final AutosaveService autosaveService = new AutosaveService(
            this::getCurrentQuestFile,
            this::currentProjectName,
            ServiceLocator.loggerFactory().create(AutosaveService.class));
    private final QuestZipGenerator questZipGenerator = new QuestZipGenerator();
    private EditorSettings userSettings = EditorSettings.defaults();
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
        userSettings = UserSettings.load();
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

        FXMLLoader menuLoader = new FXMLLoader(getClass().getResource("/dev/ftbq/editor/view/main_menu.fxml"));
        MenuController menuController = new MenuController();
        menuLoader.setController(menuController);
        MenuBar menuBar = menuLoader.load();
        menuController.configure(this, autosaveService, questZipGenerator, () -> userSettings);
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
        autosaveService.start(userSettings.autosaveIntervalMinutes());
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
        autosaveService.stop();
        logger.info("Stopping FTB Quest Editor UI");
        super.stop();
    }

    private void ensureLayoutStore() {
        if (workspace == null) {
            String workspaceProperty = System.getProperty("ftbq.editor.workspace", ".");
            workspace = Path.of(workspaceProperty).toAbsolutePath().normalize();
        }
        if (dev.ftbq.editor.services.UiServiceLocator.questLayoutStore == null) {
            dev.ftbq.editor.services.UiServiceLocator.questLayoutStore = new JsonQuestLayoutStore(workspace);
        }
    }

    public void showImportDialog() {
        ImportSnbtDialog dialog = new ImportSnbtDialog(primaryStage, importExportService, getCurrentQuestFile(), workspace);
        dialog.showAndWait().ifPresent(this::applyImportResult);
    }

    public void loadProject() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load Project");
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("FTBQ Project (*.ftbq)", "*.ftbq"));
        File file = chooser.showOpenDialog(primaryStage);
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
            autosaveService.start(userSettings.autosaveIntervalMinutes());
        } catch (Exception ex) {
            logger.error("Failed to load project", ex);
            showError("Failed to load project", ex.getMessage());
        }
    }

    public void saveProjectAs() {
        QuestFile questFile = getCurrentQuestFile();
        if (questFile == null) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Project");
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("FTBQ Project (*.ftbq)", "*.ftbq"));
        File file = chooser.showSaveDialog(primaryStage);
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

    public void saveImportedItems() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Imported Items");
        chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("JSON Files (*.json)", "*.json"));
        File file = chooser.showSaveDialog(primaryStage);
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

    public void validateCurrentPack() {
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
        autosaveService.start(userSettings.autosaveIntervalMinutes());
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

    public void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        if (primaryStage != null) {
            alert.initOwner(primaryStage);
        }
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message == null ? "An unknown error occurred." : message);
        alert.showAndWait();
    }

    private void recoverAutosaveIfPresent() {
        if (!autosaveService.hasAutosave()) {
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
            Optional<QuestFile> restored = autosaveService.readAutosave();
            if (restored.isPresent()) {
                setCurrentQuestFile(restored.get());
                updateViewModelsFromQuestFile();
                UiServiceLocator.rebuildVersionCatalog();
                logger.info("Autosave restored");
            } else {
                showError("Autosave recovery failed", "The autosave file could not be read.");
            }
        }
        autosaveService.deleteAutosave();
    }

    private Path ensureExtension(Path path, String extension) {
        String fileName = path.getFileName().toString();
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(extension)) {
            return path.resolveSibling(fileName + extension);
        }
        return path;
    }

    public synchronized QuestFile getCurrentQuestFile() {
        return currentQuestFile;
    }

    private synchronized void setCurrentQuestFile(QuestFile questFile) {
        this.currentQuestFile = Objects.requireNonNull(questFile, "questFile");
    }

    public synchronized Path getWorkspace() {
        return workspace;
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    private synchronized String currentProjectName() {
        QuestFile questFile = currentQuestFile;
        if (questFile == null) {
            return "project";
        }
        String id = questFile.id();
        if (id != null && !id.isBlank()) {
            return id;
        }
        String title = questFile.title();
        return title == null || title.isBlank() ? "project" : title;
    }

    @Override
    public synchronized int getIntervalMinutes() {
        return userSettings.autosaveIntervalMinutes();
    }

    @Override
    public synchronized void updateIntervalMinutes(int minutes) {
        if (minutes < 0) {
            throw new IllegalArgumentException("Autosave interval must be zero or greater");
        }
        userSettings = userSettings.withAutosaveInterval(minutes);
        UserSettings.save(userSettings);
        autosaveService.updateInterval(minutes);
    }

    @Override
    public synchronized boolean isZhTemplateEnabled() {
        return userSettings.createZhTemplateOnGenerate();
    }

    @Override
    public synchronized void updateZhTemplateEnabled(boolean enabled) {
        userSettings = userSettings.withZhTemplate(enabled);
        UserSettings.save(userSettings);
    }
}




