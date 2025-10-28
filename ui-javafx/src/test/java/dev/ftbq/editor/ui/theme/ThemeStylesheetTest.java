package dev.ftbq.editor.ui.theme;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

@ExtendWith(ApplicationExtension.class)
class ThemeStylesheetTest {

    private Stage stage;

    @Start
    private void start(Stage stage) {
        this.stage = stage;
        stage.setScene(new Scene(new Group(), 200, 200));
        stage.show();
    }

    @Test
    void themeStylesheetsApplyWithoutWarnings() {
        List<String> themes = List.of(
                "/css/light.css",
                "/css/dark.css",
                "/css/high_contrast.css");

        for (String theme : themes) {
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            PrintStream originalErr = System.err;
            try (PrintStream interceptor = new PrintStream(captured, true, StandardCharsets.UTF_8)) {
                System.setErr(interceptor);
                Platform.runLater(() -> stage.getScene().getStylesheets().add(resource(theme)));
                WaitForAsyncUtils.waitForFxEvents();
            } finally {
                System.setErr(originalErr);
            }
            String output = captured.toString(StandardCharsets.UTF_8);
            assertTrue(output.isBlank(), () -> theme + " emitted CSS warnings: " + output);
            Platform.runLater(() -> stage.getScene().getStylesheets().clear());
            WaitForAsyncUtils.waitForFxEvents();
        }
    }

    private String resource(String path) {
        var url = getClass().getResource(path);
        if (url == null) {
            throw new IllegalStateException("Missing stylesheet: " + path);
        }
        return url.toExternalForm();
    }
}
