package dev.ftbq.editor.controller;

import dev.ftbq.editor.ThemeService;
import dev.ftbq.editor.service.UserSettings;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;

public class SettingsController {
    @FXML private CheckBox chkShowGrid;
    @FXML private CheckBox chkDarkTheme;
    @FXML private CheckBox chkSmoothPanning;
    @FXML private Spinner<Integer> spnAutosave;

    private Scene scene;
    public void setScene(Scene scene) { this.scene = scene; }

    @FXML
    public void initialize() {
        var es = UserSettings.get();
        chkShowGrid.setSelected(es.showGrid);
        chkDarkTheme.setSelected(es.darkTheme);
        chkSmoothPanning.setSelected(es.smoothPanning);
        spnAutosave.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 120, es.autosaveMinutes));

        chkShowGrid.selectedProperty().addListener((o,ov,nv)->{
            es.showGrid = nv; UserSettings.save(es);
            // canvas listens via controller binding (see ChapterEditorController)
        });
        chkDarkTheme.selectedProperty().addListener((o,ov,nv)->{
            es.darkTheme = nv; UserSettings.save(es);
            if (scene != null) ThemeService.apply(scene, nv);
        });
        chkSmoothPanning.selectedProperty().addListener((o,ov,nv)->{
            es.smoothPanning = nv; UserSettings.save(es);
        });
        spnAutosave.valueProperty().addListener((o,ov,nv)->{
            es.autosaveMinutes = Math.max(0, nv == null ? 1 : nv);
            UserSettings.save(es);
        });
    }
}
