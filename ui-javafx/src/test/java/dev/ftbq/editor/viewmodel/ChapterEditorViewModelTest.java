package dev.ftbq.editor.viewmodel;

import dev.ftbq.editor.domain.Chapter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ChapterEditorViewModelTest {

    @Test
    void loadSampleChapterProvidesGraphData() {
        ChapterEditorViewModel viewModel = new ChapterEditorViewModel();
        viewModel.loadSampleChapter();

        Chapter chapter = viewModel.getChapter();
        assertNotNull(chapter, "Expected a chapter to be loaded");
        assertEquals("chapter_demo", chapter.id());
        assertEquals(4, chapter.quests().size(), "Sample chapter should contain four quests");
    }
}
