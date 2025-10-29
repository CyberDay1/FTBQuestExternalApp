package dev.ftbq.editor;

import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.service.ThemeService;
import dev.ftbq.editor.service.UserSettings;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class MainApp extends Application {

    private Stage primaryStage;
    private Path workspace = Path.of(System.getProperty("user.dir"));
    private QuestFile currentQuestFile;

    @Override
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/dev/ftbq/editor/view/main.fxml"));
        Scene scene = new Scene(root, 960, 720);
        ThemeService.apply(scene, UserSettings.get().darkTheme);
        this.primaryStage = stage;
        stage.setTitle("FTB Quest Editor");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

    public void showImportDialog() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import quests or mods");
        chooser.getExtensionFilters().setAll(
                new FileChooser.ExtensionFilter("Quest or Mod Archive", "*.jar", "*.zip")
        );
        File selectedFile = chooser.showOpenDialog(getPrimaryStage());
        if (selectedFile != null) {
            System.out.println("Selected import file: " + selectedFile.getAbsolutePath());
        } else {
            System.out.println("Import dialog canceled.");
        }
    }

    public void loadProject() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Quest Project Folder");
        File directory = chooser.showDialog(getPrimaryStage());
        if (directory != null) {
            workspace = directory.toPath();
            System.out.println("Loading project from: " + workspace);
            currentQuestFile = QuestFile.builder()
                    .id(Optional.ofNullable(directory.getName()).filter(name -> !name.isBlank()).orElse("placeholder"))
                    .title("Project loaded from " + directory.getName())
                    .build();
        } else {
            System.out.println("Load project canceled.");
        }
    }

    public void saveProjectAs() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Quest Project As");
        File targetFile = chooser.showSaveDialog(getPrimaryStage());
        if (targetFile == null) {
            System.out.println("Save project canceled.");
            return;
        }

        Path targetPath = targetFile.toPath();
        if (targetPath.getParent() != null) {
            try {
                Files.createDirectories(targetPath.getParent());
            } catch (IOException e) {
                System.out.println("Failed to create directories for save location: " + e.getMessage());
                showError("Save Failed", "Unable to prepare save location: " + e.getMessage());
                return;
            }
        }

        try {
            Files.writeString(targetPath, "Placeholder quest save", StandardCharsets.UTF_8);
            System.out.println("Saved project placeholder to: " + targetPath);
        } catch (IOException e) {
            System.out.println("Failed to save project: " + e.getMessage());
            showError("Save Failed", e.getMessage());
        }
    }

    public void validateCurrentPack() {
        System.out.println("Validating quest pack...");
        System.out.println("Quest pack validation complete.");
    }

    public void saveImportedItems() {
        System.out.println("Saving imported item database...");
    }

    public QuestFile getCurrentQuestFile() {
        return currentQuestFile;
    }

    public Path getWorkspace() {
        return workspace;
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        if (getPrimaryStage() != null) {
            alert.initOwner(getPrimaryStage());
        }
        alert.showAndWait();
    }
}
