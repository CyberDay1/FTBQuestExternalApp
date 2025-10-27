package dev.ftbq.editor.controller;

import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.view.graph.GraphCanvas;
import dev.ftbq.editor.viewmodel.ChapterEditorViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;

import java.util.Objects;

/**
 * Controller responsible for presenting a quest chapter graph.
 */
public class ChapterEditorController {

    @FXML
    private StackPane graphContainer;

    @FXML
    private Label chapterTitleLabel;

    private final GraphCanvas graphCanvas = new GraphCanvas();
    private ChapterEditorViewModel viewModel;

    @FXML
    public void initialize() {
        if (graphContainer != null) {
            graphCanvas.setMinSize(0, 0);
            graphCanvas.setPrefSize(StackPane.USE_COMPUTED_SIZE, StackPane.USE_COMPUTED_SIZE);
            graphCanvas.prefWidthProperty().bind(graphContainer.widthProperty());
            graphCanvas.prefHeightProperty().bind(graphContainer.heightProperty());
            graphContainer.getChildren().add(graphCanvas);
        }
        if (viewModel != null && viewModel.getChapter() != null) {
            applyChapter(viewModel.getChapter());
        }
    }

    public void setViewModel(ChapterEditorViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.viewModel.chapterProperty().addListener((obs, oldChapter, newChapter) -> applyChapter(newChapter));
        if (newChapterAvailable()) {
            applyChapter(viewModel.getChapter());
        }
    }

    private boolean newChapterAvailable() {
        return viewModel != null && viewModel.getChapter() != null;
    }

    private void applyChapter(Chapter chapter) {
        if (chapter == null) {
            graphCanvas.setChapter(null);
            if (chapterTitleLabel != null) {
                chapterTitleLabel.setText("");
            }
            return;
        }
        if (chapterTitleLabel != null) {
            chapterTitleLabel.setText(chapter.title());
        }
        graphCanvas.setChapter(chapter);
    }
}
