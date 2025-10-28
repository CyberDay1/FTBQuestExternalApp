package dev.ftbq.editor.viewmodel;

import dev.ftbq.editor.domain.BackgroundRef;
import dev.ftbq.editor.domain.QuestFile;
import javafx.beans.Observable;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ChapterGroupBrowserViewModel {
    private final ObservableList<ChapterGroup> chapterGroups = FXCollections.observableArrayList(
            group -> new Observable[]{group.nameProperty(), group.getChapters()}
    );
    private final StringProperty searchText = new SimpleStringProperty("");

    public ChapterGroupBrowserViewModel() {
        ChapterGroup starterGroup = addGroup("Getting Started");
        addChapter(starterGroup, "Welcome");
        addChapter(starterGroup, "First Steps");
    }

    public ObservableList<ChapterGroup> getChapterGroups() {
        return chapterGroups;
    }

    public StringProperty searchTextProperty() {
        return searchText;
    }

    public ChapterGroup addGroup(String name) {
        ChapterGroup group = new ChapterGroup(name);
        chapterGroups.add(group);
        return group;
    }

    public void removeGroup(ChapterGroup group) {
        chapterGroups.remove(group);
    }

    public void renameGroup(ChapterGroup group, String newName) {
        Objects.requireNonNull(group, "Group cannot be null");
        group.setName(newName);
    }

    public void moveGroupUp(ChapterGroup group) {
        moveGroup(group, -1);
    }

    public void moveGroupDown(ChapterGroup group) {
        moveGroup(group, 1);
    }

    private void moveGroup(ChapterGroup group, int delta) {
        int index = chapterGroups.indexOf(group);
        if (index == -1) {
            return;
        }
        int newIndex = index + delta;
        if (newIndex < 0 || newIndex >= chapterGroups.size()) {
            return;
        }
        chapterGroups.remove(index);
        chapterGroups.add(newIndex, group);
    }

    public Chapter addChapter(ChapterGroup group, String name) {
        Objects.requireNonNull(group, "Group cannot be null");
        Chapter chapter = new Chapter(name);
        group.getChapters().add(chapter);
        return chapter;
    }

    public void removeChapter(ChapterGroup group, Chapter chapter) {
        Objects.requireNonNull(group, "Group cannot be null");
        group.getChapters().remove(chapter);
    }

    public void renameChapter(Chapter chapter, String newName) {
        Objects.requireNonNull(chapter, "Chapter cannot be null");
        chapter.setName(newName);
    }

    public void moveChapterUp(ChapterGroup group, Chapter chapter) {
        moveChapter(group, chapter, -1);
    }

    public void moveChapterDown(ChapterGroup group, Chapter chapter) {
        moveChapter(group, chapter, 1);
    }

    public void loadFromQuestFile(QuestFile questFile) {
        Objects.requireNonNull(questFile, "questFile");
        chapterGroups.clear();
        var chapterMap = questFile.chapters().stream()
                .collect(Collectors.toMap(dev.ftbq.editor.domain.Chapter::id, chapter -> chapter, (a, b) -> a, LinkedHashMap::new));
        Set<String> assigned = new LinkedHashSet<>();
        if (!questFile.chapterGroups().isEmpty()) {
            for (dev.ftbq.editor.domain.ChapterGroup groupData : questFile.chapterGroups()) {
                ChapterGroup group = addGroup(groupData.title());
                for (String chapterId : groupData.chapterIds()) {
                    dev.ftbq.editor.domain.Chapter domainChapter = chapterMap.get(chapterId);
                    Chapter chapter = addChapter(group, domainChapter != null ? domainChapter.title() : chapterId);
                    if (domainChapter != null) {
                        chapter.setBackground(domainChapter.background());
                    }
                    assigned.add(chapterId);
                }
            }
        }
        var unassigned = questFile.chapters().stream()
                .filter(chapter -> !assigned.contains(chapter.id()))
                .toList();
        if (!unassigned.isEmpty()) {
            ChapterGroup group = addGroup("Ungrouped Chapters");
            for (dev.ftbq.editor.domain.Chapter chapter : unassigned) {
                Chapter viewChapter = addChapter(group, chapter.title());
                viewChapter.setBackground(chapter.background());
            }
        }
    }

    private void moveChapter(ChapterGroup group, Chapter chapter, int delta) {
        Objects.requireNonNull(group, "Group cannot be null");
        ObservableList<Chapter> chapters = group.getChapters();
        int index = chapters.indexOf(chapter);
        if (index == -1) {
            return;
        }
        int newIndex = index + delta;
        if (newIndex < 0 || newIndex >= chapters.size()) {
            return;
        }
        chapters.remove(index);
        chapters.add(newIndex, chapter);
    }

    public static final class ChapterGroup {
        private final StringProperty name = new SimpleStringProperty();
        private final ObservableList<Chapter> chapters = FXCollections.observableArrayList(
                chapter -> new Observable[]{chapter.nameProperty(), chapter.backgroundProperty()}
        );

        public ChapterGroup(String name) {
            setName(name);
        }

        public String getName() {
            return name.get();
        }

        public void setName(String name) {
            this.name.set(name);
        }

        public StringProperty nameProperty() {
            return name;
        }

        public ObservableList<Chapter> getChapters() {
            return chapters;
        }
    }

    public static final class Chapter {
        private final StringProperty name = new SimpleStringProperty();
        private final javafx.beans.property.ObjectProperty<BackgroundRef> background = new javafx.beans.property.SimpleObjectProperty<>(new BackgroundRef("minecraft:textures/gui/default.png"));

        public Chapter(String name) {
            setName(name);
        }

        public String getName() {
            return name.get();
        }

        public void setName(String name) {
            this.name.set(name);
        }

        public StringProperty nameProperty() {
            return name;
        }

        public BackgroundRef getBackground() {
            return background.get();
        }

        public void setBackground(BackgroundRef background) {
            this.background.set(background == null ? new BackgroundRef("minecraft:textures/gui/default.png") : background);
        }

        public javafx.beans.property.ObjectProperty<BackgroundRef> backgroundProperty() {
            return background;
        }
    }
}


