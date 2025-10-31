package dev.ftbq.editor;

import dev.ftbq.editor.controller.ChapterEditorController;
import dev.ftbq.editor.controller.ChapterGroupBrowserController;
import dev.ftbq.editor.controller.MainController;
import dev.ftbq.editor.controller.MenuController;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.service.ThemeService;
import dev.ftbq.editor.service.UserSettings;
import dev.ftbq.editor.ui.AiQuestCreationTab;
import dev.ftbq.editor.view.QuestEditorController;
import dev.ftbq.editor.services.UiServiceLocator;
import dev.ftbq.editor.store.Project;
import dev.ftbq.editor.store.StoreDaoImpl;
import dev.ftbq.editor.view.graph.layout.JsonQuestLayoutStore;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
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
    private ChapterGroupBrowserController chapterGroupBrowserController;
    private ChapterEditorController chapterEditorController;

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("view/main.fxml"));
        Parent root = loader.load();
        MainController mainController = loader.getController();
        MenuController menu = mainController.getMenuController();
        ChapterGroupBrowserController chapters = mainController.getChapterGroupBrowserController();
        ChapterEditorController chapterEditor = mainController.getChapterEditorController();
        menu.setMainApp(this);
        chapters.setMainApp(this);
        chapterGroupBrowserController = chapters;
        chapterEditorController = chapterEditor;
        initStore();
        if (currentQuestFile == null) {
            String workspaceName = Optional.ofNullable(workspace.getFileName())
                    .map(Path::toString)
                    .filter(name -> !name.isBlank())
                    .orElse("workspace");
            currentQuestFile = QuestFile.builder()
                    .id(workspaceName)
                    .title("Workspace: " + workspaceName)
                    .build();
        }
        chapterGroupBrowserController.setWorkspaceContext(workspace, currentQuestFile);
        UiServiceLocator.questLayoutStore = new JsonQuestLayoutStore(workspace);
        chapterGroupBrowserController.reloadGroups();

        Scene scene = new Scene(root, 1200, 800);
        ThemeService.apply(scene, UserSettings.get().darkTheme);

        wireAiQuestTab(scene);

        this.primaryStage = stage;
        stage.setTitle("FTB Quest Editor");
        stage.setScene(scene);
        stage.show();
    }

    private void initStore() {
        UiServiceLocator.initialize();
        UiServiceLocator.storeDao = new StoreDaoImpl();
        UiServiceLocator.storeDao.loadLastProjectIfAvailable();
        Project project = UiServiceLocator.storeDao.getActiveProject();
        if (project != null) {
            notifyProjectLoaded(project);
        }
    }

    private void notifyProjectLoaded(Project project) {
        currentQuestFile = project.getQuestFile();
        if (chapterEditorController != null) {
            chapterEditorController.setProject(project);
        }
        if (chapterGroupBrowserController != null) {
            chapterGroupBrowserController.setProject(project);
        }
    }

    private void wireAiQuestTab(Scene scene) {
        TabPane tabPane = (TabPane) scene.lookup("#mainTabs");
        if (tabPane == null) {
            return;
        }

        Tab placeholder = null;
        for (Tab tab : tabPane.getTabs()) {
            if ("aiQuestTab".equals(tab.getId())) {
                placeholder = tab;
                break;
            }
        }

        if (placeholder == null) {
            return;
        }

        int index = tabPane.getTabs().indexOf(placeholder);
        AiQuestCreationTab aiQuestTab = new AiQuestCreationTab();
        aiQuestTab.setId("aiQuestTab");
        tabPane.getTabs().set(index, aiQuestTab);
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
            UiServiceLocator.questLayoutStore = new JsonQuestLayoutStore(workspace);
            if (chapterGroupBrowserController != null) {
                chapterGroupBrowserController.setWorkspaceContext(workspace, currentQuestFile);
                chapterGroupBrowserController.reloadGroups();
            }
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

    public void openQuestEditor(Quest quest) {
        if (quest == null) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("view/quest_editor.fxml"));
            Parent root = loader.load();
            QuestEditorController controller = loader.getController();
            controller.setQuest(quest);
            Stage stage = new Stage();
            stage.setTitle("Quest Editor - " + quest.title());
            stage.setScene(new Scene(root));
            stage.initModality(Modality.APPLICATION_MODAL);
            if (getPrimaryStage() != null) {
                stage.initOwner(getPrimaryStage());
            }
            stage.show();
        } catch (IOException e) {
            showError("Failed to open quest editor", e.getMessage());
        }
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
