package dev.ftbq.editor;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

public class MainApp extends Application {
    @Override
    public void start(Stage primaryStage) {
        Platform.exit();
    }

    public static void main(String[] args) {
        try {
            dev.ftbq.editor.store.Database.init();
        } catch (Exception e) {
            e.printStackTrace();
        }
        launch(args);
    }
}
