package dev.ftbq.editor.controller;

import dev.ftbq.editor.MainApp;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.service.AutosaveService;
import dev.ftbq.editor.service.QuestZipGenerator;
import dev.ftbq.editor.service.UserSettings.EditorSettings;
import dev.ftbq.editor.services.logging.StructuredLogger;
import dev.ftbq.editor.services.bus.ServiceLocator;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Controller for the main menu actions.
 */
public final class MenuController {

    private final StructuredLogger logger = ServiceLocator.loggerFactory().create(MenuController.class);
    private MainApp mainApp;
    private AutosaveService autosaveService;
    private QuestZipGenerator questZipGenerator;
    private Supplier<EditorSettings> settingsSupplier;

    public void configure(MainApp mainApp,
                          AutosaveService autosaveService,
                          QuestZipGenerator questZipGenerator,
                          Supplier<EditorSettings> settingsSupplier) {
        this.mainApp = Objects.requireNonNull(mainApp, "mainApp");
        this.autosaveService = Objects.requireNonNull(autosaveService, "autosaveService");
        this.questZipGenerator = Objects.requireNonNull(questZipGenerator, "questZipGenerator");
        this.settingsSupplier = Objects.requireNonNull(settingsSupplier, "settingsSupplier");
    }

    @FXML
    private void onImportQuests() {
        mainApp.showImportDialog();
    }

    @FXML
    private void onLoadProject() {
        mainApp.loadProject();
    }

    @FXML
    private void onSaveProjectAs() {
        mainApp.saveProjectAs();
    }

    @FXML
    private void onValidateQuestPack() {
        mainApp.validateCurrentPack();
    }

    @FXML
    private void onSaveImportedItems() {
        mainApp.saveImportedItems();
    }

    @FXML
    private void onGenerateQuestZip() {
        QuestFile questFile = mainApp.getCurrentQuestFile();
        if (questFile == null) {
            mainApp.showError("No quest data", "There is no quest file loaded to export.");
            return;
        }
        try {
            autosaveService.flushNow();
            EditorSettings settings = settingsSupplier.get();
            if (settings == null) {
                settings = EditorSettings.defaults();
            }
            Path generated = questZipGenerator.generate(questFile, mainApp.getWorkspace(), settings);
            boolean expectChapterFiles = questFile.chapters() != null && !questFile.chapters().isEmpty();
            boolean expectRewardFiles = questFile.lootTables() != null && !questFile.lootTables().isEmpty();
            validateArchive(generated, expectChapterFiles, expectRewardFiles);
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Generate Quest .zip");
            chooser.getExtensionFilters().setAll(new FileChooser.ExtensionFilter("Quest Zip (*.zip)", "*.zip"));
            chooser.setInitialFileName("ftbquests.zip");
            File targetFile = chooser.showSaveDialog(mainApp.getPrimaryStage());
            if (targetFile == null) {
                return;
            }
            Path targetPath = ensureZipExtension(targetFile.toPath());
            if (targetPath.getParent() != null) {
                Files.createDirectories(targetPath.getParent());
            }
            Files.copy(generated, targetPath, StandardCopyOption.REPLACE_EXISTING);
            logger.info("Quest zip generated", StructuredLogger.field("path", targetPath.toString()));
            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Quest archive written to " + targetPath, ButtonType.OK);
            if (mainApp.getPrimaryStage() != null) {
                alert.initOwner(mainApp.getPrimaryStage());
            }
            alert.setHeaderText("Quest archive generated");
            alert.setTitle("Generation complete");
            alert.showAndWait();
        } catch (Exception ex) {
            logger.error("Failed to generate quest zip", ex);
            mainApp.showError("Failed to generate quest zip", ex.getMessage());
        }
    }

    private void validateArchive(Path zipPath, boolean expectChapterFiles, boolean expectRewardFiles) throws IOException {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            ensureEntry(zip, "quests/");
            ensureEntry(zip, "quests/chapter_groups.snbt");
            ensureEntry(zip, "quests/data.snbt");
            ensureEntry(zip, "quests/lang/en_us.snbt");
            ensureEntry(zip, "quests/chapters/");
            ensureEntry(zip, "quests/reward_tables/");
            if (expectChapterFiles) {
                ensureNestedFile(zip, "quests/chapters/");
            }
            if (expectRewardFiles) {
                ensureNestedFile(zip, "quests/reward_tables/");
            }
        }
    }

    private void ensureEntry(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            throw new IOException("Generated archive missing required entry: " + name);
        }
    }

    private void ensureNestedFile(ZipFile zip, String directory) throws IOException {
        boolean present = zip.stream()
                .anyMatch(entry -> entry.getName().startsWith(directory) && !entry.isDirectory() && !entry.getName().equals(directory));
        if (!present) {
            throw new IOException("Generated archive contains no files under " + directory);
        }
    }

    private Path ensureZipExtension(Path path) {
        String lower = path.getFileName().toString().toLowerCase();
        if (!lower.endsWith(".zip")) {
            return path.resolveSibling(path.getFileName() + ".zip");
        }
        return path;
    }
}
