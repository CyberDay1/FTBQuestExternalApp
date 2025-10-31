package dev.ftbq.editor.controller;

import javafx.fxml.FXML;

public class MainController {

    @FXML
    private MenuController menuController;

    @FXML
    private ChapterGroupBrowserController chapterGroupBrowserController;

    @FXML
    private ChapterEditorController chapterEditorController;

    public MenuController getMenuController() {
        return menuController;
    }

    public ChapterGroupBrowserController getChapterGroupBrowserController() {
        return chapterGroupBrowserController;
    }

    public ChapterEditorController getChapterEditorController() {
        return chapterEditorController;
    }
}
