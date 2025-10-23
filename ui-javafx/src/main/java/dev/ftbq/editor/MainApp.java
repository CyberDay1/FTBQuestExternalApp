package dev.ftbq.editor;

import dev.ftbq.editor.controller.LootTableEditorController;
import dev.ftbq.editor.controller.QuestEditorController;
import dev.ftbq.editor.viewmodel.ChapterGroupBrowserViewModel;
import dev.ftbq.editor.view.ChapterGroupBrowserController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApp extends Application {
    @Override
    public void start(Stage primaryStage) throws IOException {
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
            e.printStackTrace();
        }

        Scene scene = new Scene(tabPane);

        primaryStage.setTitle("FTB Quest Editor");
        primaryStage.setScene(scene);
        primaryStage.setWidth(960);
        primaryStage.setHeight(720);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
