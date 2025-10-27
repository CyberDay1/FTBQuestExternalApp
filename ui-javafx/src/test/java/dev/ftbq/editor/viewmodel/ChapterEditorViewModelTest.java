package dev.ftbq.editor.viewmodel;

import dev.ftbq.editor.domain.Chapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


class ChapterEditorViewModelTest {

    @Test
    void loadSampleChaptersProvidesGraphData() {
        ChapterEditorViewModel viewModel = new ChapterEditorViewModel();
        viewModel.loadSampleChapters();

        Chapter chapter = viewModel.getChapter();
        assertNotNull(chapter, "Expected a chapter to be loaded");
        assertEquals("chapter_getting_started", chapter.id());
        assertEquals(4, chapter.quests().size(), "Sample chapter should contain four quests");
    }

    @Test
    void filterReducesVisibleChapters() {
        ChapterEditorViewModel viewModel = new ChapterEditorViewModel();
        viewModel.loadSampleChapters();

        viewModel.chapterFilterProperty().set("exploration");

        assertEquals(1, viewModel.getChapters().size());
        assertEquals("chapter_exploration", viewModel.getChapters().get(0).id());
    }
}
