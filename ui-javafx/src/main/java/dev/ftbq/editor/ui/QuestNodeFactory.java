package dev.ftbq.editor.ui;

import dev.ftbq.editor.domain.Quest;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Factory for quest node UI elements used in the chapter editor grid.
 */
public final class QuestNodeFactory {

    private static final double DEFAULT_WIDTH = 160;
    private static final double DEFAULT_HEIGHT = 96;

    private QuestNodeFactory() {
    }

    public static Node create(Quest quest, Consumer<Quest> questEditorOpener) {
        Objects.requireNonNull(quest, "quest");
        Objects.requireNonNull(questEditorOpener, "questEditorOpener");

        Label title = new Label(quest.title());
        title.getStyleClass().add("quest-node-title");
        title.setWrapText(true);
        title.setAlignment(Pos.CENTER);

        StackPane container = new StackPane(title);
        container.setAlignment(Pos.CENTER);
        container.getStyleClass().addAll("quest-node", "quest-node-button");
        container.setPrefSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        container.setMinSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        container.setMaxSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);

        container.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                questEditorOpener.accept(quest);
                event.consume();
            }
        });

        return container;
    }
}
