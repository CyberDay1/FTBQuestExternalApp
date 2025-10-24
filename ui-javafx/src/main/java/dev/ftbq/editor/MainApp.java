package dev.ftbq.editor;

import dev.ftbq.editor.controller.LootTableEditorController;
import dev.ftbq.editor.controller.QuestEditorController;
import dev.ftbq.editor.viewmodel.ChapterGroupBrowserViewModel;
import dev.ftbq.editor.view.ChapterGroupBrowserController;
import dev.ftbq.editor.services.bus.ServiceLocator;
import dev.ftbq.editor.services.logging.StructuredLogger;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApp extends Application {
    private final StructuredLogger logger = ServiceLocator.loggerFactory().create(MainApp.class);

    @Override
    public void start(Stage primaryStage) throws IOException {
        logger.info("Starting FTB Quest Editor UI");
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

        FXMLLoader lootLoader = new FXMLLoader(getClass().getResource("/dev/ftbq/editor/view/loot_table_editor.fxml"));
        Parent lootRoot = lootLoader.load();
        LootTableEditorController lootTableController = lootLoader.getController();
        Tab lootTab = new Tab("Loot Tables", lootRoot);
        lootTab.setClosable(false);
        tabPane.getTabs().add(lootTab);

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/dev/ftbq/editor/view/quest_editor.fxml"));
            Parent questRoot = loader.load();
            QuestEditorController questController = loader.getController();
            questController.setLootTableLinkHandler(tableId -> {
                tabPane.getSelectionModel().select(lootTab);
                lootTableController.focusOnTable(tableId);
            });
            Tab questTab = new Tab("Quest Editor", questRoot);
            questTab.setClosable(false);
            tabPane.getTabs().add(questTab);
        } catch (Exception e) {
            logger.error("Failed to load quest editor UI", e);
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
        logger.info("Stopping FTB Quest Editor UI");
        super.stop();
    }
}
