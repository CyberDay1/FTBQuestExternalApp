package dev.ftbq.editor.view.dialog;

import dev.ftbq.editor.validation.ValidationIssue;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Window;

/**
 * Modal dialog that displays validation issues and allows jumping to affected quests.
 */
public final class ValidationResultsDialog extends Dialog<Void> {

    public ValidationResultsDialog(Window owner, List<ValidationIssue> issues, Consumer<ValidationIssue> jumpHandler) {
        Objects.requireNonNull(issues, "issues");
        Objects.requireNonNull(jumpHandler, "jumpHandler");
        setTitle("Validation Issues");
        getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        if (owner != null) {
            initOwner(owner);
        }
        ListView<ValidationIssue> listView = new ListView<>(FXCollections.observableArrayList(issues));
        listView.setCellFactory(list -> new ValidationIssueCell(jumpHandler));
        getDialogPane().setContent(listView);
        getDialogPane().setPrefWidth(500);
        getDialogPane().setPrefHeight(360);
        setResizable(true);
    }

    private static final class ValidationIssueCell extends ListCell<ValidationIssue> {
        private final Consumer<ValidationIssue> jumpHandler;
        private final Label messageLabel = new Label();
        private final Label pathLabel = new Label();
        private final Button jumpButton = new Button("Jump to");
        private final HBox container = new HBox(12);

        private ValidationIssueCell(Consumer<ValidationIssue> jumpHandler) {
            this.jumpHandler = Objects.requireNonNull(jumpHandler, "jumpHandler");
            messageLabel.setWrapText(true);
            pathLabel.getStyleClass().add("validation-path");
            HBox.setHgrow(messageLabel, Priority.ALWAYS);
            container.getChildren().addAll(messageLabel, pathLabel, jumpButton);
            jumpButton.setOnAction(event -> {
                ValidationIssue issue = getItem();
                if (issue != null) {
                    jumpHandler.accept(issue);
                }
            });
        }

        @Override
        protected void updateItem(ValidationIssue item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                messageLabel.setText(item.message());
                pathLabel.setText(item.path());
                setGraphic(container);
            }
        }
    }
}
