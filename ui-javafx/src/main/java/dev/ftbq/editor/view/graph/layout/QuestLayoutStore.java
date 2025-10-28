package dev.ftbq.editor.view.graph.layout;

import javafx.geometry.Point2D;

import java.util.Optional;

/**
 * Persisted storage for quest node layout information.
 */
public interface QuestLayoutStore {

    Optional<Point2D> getNodePos(String chapterId, String questId);

    void putNodePos(String chapterId, String questId, double x, double y);

    void removeQuest(String chapterId, String questId);

    void removeChapter(String chapterId);

    void flush();
}
