package dev.ftbq.editor;

import dev.ftbq.editor.viewmodel.ChapterGroupBrowserViewModel;
import dev.ftbq.editor.view.ChapterGroupBrowserController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class MainApp extends Application {
    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/dev/ftbq/editor/view/chapter_group_browser.fxml"));
        Scene scene = new Scene(loader.load());

        ChapterGroupBrowserController controller = loader.getController();
        ChapterGroupBrowserViewModel viewModel = new ChapterGroupBrowserViewModel();
        controller.setViewModel(viewModel);

        primaryStage.setTitle("FTB Quest Chapter Browser");
        primaryStage.setScene(scene);
        primaryStage.setWidth(420);
        primaryStage.setHeight(600);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
