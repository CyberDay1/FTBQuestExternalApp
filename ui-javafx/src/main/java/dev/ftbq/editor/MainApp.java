package dev.ftbq.editor;

import dev.ftbq.editor.service.ThemeService;
import dev.ftbq.editor.service.UserSettings;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class MainApp extends Application {
    @Override
    public void start(Stage stage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/dev/ftbq/editor/view/main.fxml"));
        Scene scene = new Scene(root, 960, 720);
        ThemeService.apply(scene, UserSettings.get().darkTheme);
        stage.setTitle("FTB Quest Editor");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
