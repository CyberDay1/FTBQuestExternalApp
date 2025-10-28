package dev.ftbq.editor;

import dev.ftbq.editor.controller.ChapterEditorController;
import dev.ftbq.editor.controller.LootTableEditorController;
import dev.ftbq.editor.viewmodel.ChapterGroupBrowserViewModel;
import dev.ftbq.editor.viewmodel.ChapterEditorViewModel;
import dev.ftbq.editor.view.ChapterGroupBrowserController;
import dev.ftbq.editor.services.UiServiceLocator;
import dev.ftbq.editor.services.bus.ServiceLocator;
import dev.ftbq.editor.services.logging.StructuredLogger;
import dev.ftbq.editor.view.graph.layout.JsonQuestLayoutStore;
import dev.ftbq.editor.view.graph.layout.QuestLayoutStore;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Path;

public class MainApp extends Application {
    private final StructuredLogger logger = ServiceLocator.loggerFactory().create(MainApp.class);

    @Override
    public void start(Stage primaryStage) throws IOException {
        logger.info("Starting FTB Quest Editor UI");
        ensureLayoutStore();
        FXMLLoader chapterLoader = new FXMLLoader(getClass().getResource("/dev/ftbq/editor/view/chapter_group_browser.fxml"));
        Parent chapterRoot = chapterLoader.load();

        ChapterGroupBrowserController chapterController = chapterLoader.getController();
        ChapterGroupBrowserViewModel chapterViewModel = new ChapterGroupBrowserViewModel();
        chapterController.setViewModel(chapterViewModel);

        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        Tab chapterTab = new Tab("Chapter Groups");
        chapterTab.setContent(chapterRoot);
        chapterTab.setClosable(false);

        tabPane.getTabs().add(chapterTab);

        FXMLLoader chapterEditorLoader = new FXMLLoader(getClass().getResource("/dev/ftbq/editor/view/chapter_editor.fxml"));
        Parent chapterEditorRoot = chapterEditorLoader.load();
        ChapterEditorController chapterEditorController = chapterEditorLoader.getController();
        ChapterEditorViewModel chapterEditorViewModel = new ChapterEditorViewModel();
        chapterEditorController.setViewModel(chapterEditorViewModel);
        chapterEditorViewModel.loadSampleChapters();
        Tab chapterEditorTab = new Tab("Chapter Editor", chapterEditorRoot);
        chapterEditorTab.setClosable(false);
        tabPane.getTabs().add(chapterEditorTab);

        FXMLLoader lootLoader = new FXMLLoader(getClass().getResource("/dev/ftbq/editor/view/loot_table_editor.fxml"));
        Parent lootRoot = lootLoader.load();
        LootTableEditorController lootTableController = lootLoader.getController();
        Tab lootTab = new Tab("Loot Tables", lootRoot);
        lootTab.setClosable(false);
        tabPane.getTabs().add(lootTab);

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

        Scene scene = new Scene(tabPane);

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
        if (UiServiceLocator.questLayoutStore != null) {
            return;
        }
        String workspaceProperty = System.getProperty("ftbq.editor.workspace", ".");
        Path workspace = Path.of(workspaceProperty).toAbsolutePath().normalize();
        UiServiceLocator.questLayoutStore = new JsonQuestLayoutStore(workspace);
    }
}




