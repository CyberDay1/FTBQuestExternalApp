package dev.ftbq.editor;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {
    private static final String APPLICATION_TITLE = "FTB Quests Editor";

    private final WindowSettings windowSettings = new WindowSettings();

    @Override
    public void start(Stage primaryStage) {
        NavigationPane navigationPane = new NavigationPane();
        Scene scene = new Scene(navigationPane);

        primaryStage.setTitle(APPLICATION_TITLE);
        primaryStage.setScene(scene);

        windowSettings.apply(primaryStage);
        windowSettings.observe(primaryStage);

        primaryStage.show();
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
