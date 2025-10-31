package dev.ftbq.editor.controller;

import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.ItemReward;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.RewardCommand;
import dev.ftbq.editor.domain.Task;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Controller backing the quest editor dialog.
 */
public final class QuestEditorController {

    @FXML
    private Label questIdLabel;

    @FXML
    private TextField questNameField;

    @FXML
    private TextArea descriptionArea;

    @FXML
    private TextField iconIdField;

    @FXML
    private TextField iconPathField;

    @FXML
    private TextField visibilityField;

    @FXML
    private TextField xpAmountField;

    @FXML
    private TextField xpLevelsField;

    @FXML
    private TextField lootTableField;

    @FXML
    private TextField commandField;

    @FXML
    private TextField commandRunAsField;

    @FXML
    private ListView<String> taskList;

    @FXML
    private ListView<String> rewardList;

    @FXML
    private ListView<String> dependencyList;

    private Quest quest;

    @FXML
    private void initialize() {
        if (descriptionArea != null) {
            descriptionArea.setWrapText(true);
            descriptionArea.setEditable(false);
        }
        setReadOnly(questNameField, iconIdField, iconPathField, visibilityField,
                xpAmountField, xpLevelsField, lootTableField, commandField, commandRunAsField);

        if (taskList != null) {
            taskList.setItems(FXCollections.observableArrayList());
        }
        if (rewardList != null) {
            rewardList.setItems(FXCollections.observableArrayList());
        }
        if (dependencyList != null) {
            dependencyList.setItems(FXCollections.observableArrayList());
        }
    }

    private void setReadOnly(TextField... fields) {
        for (TextField field : fields) {
            if (field != null) {
                field.setEditable(false);
            }
        }
    }

    public void setQuest(Quest quest) {
        this.quest = Objects.requireNonNull(quest, "quest");

        if (questIdLabel != null) {
            questIdLabel.setText(quest.id());
        }
        if (questNameField != null) {
            questNameField.setText(quest.title());
        }
        if (descriptionArea != null) {
            descriptionArea.setText(quest.description());
        }

        IconRef icon = quest.icon();
        if (iconIdField != null) {
            iconIdField.setText(icon.icon());
        }
        if (iconPathField != null) {
            iconPathField.setText(icon.relativePath().orElse(""));
        }

        if (visibilityField != null) {
            visibilityField.setText(quest.visibility().name());
        }
        if (xpAmountField != null) {
            xpAmountField.setText(quest.experienceAmountOptional().map(String::valueOf).orElse(""));
        }
        if (xpLevelsField != null) {
            xpLevelsField.setText(quest.experienceLevelsOptional().map(String::valueOf).orElse(""));
        }
        if (lootTableField != null) {
            lootTableField.setText(quest.lootTableIdOptional().orElse(""));
        }

        RewardCommand commandReward = quest.commandReward();
        if (commandField != null) {
            commandField.setText(commandReward != null ? commandReward.command() : "");
        }
        if (commandRunAsField != null) {
            commandRunAsField.setText(commandReward == null
                    ? ""
                    : (commandReward.runAsServer() ? "Server" : "Player"));
        }

        if (taskList != null) {
            taskList.getItems().setAll(quest.tasks().stream()
                    .map(this::describeTask)
                    .collect(Collectors.toList()));
        }
        if (rewardList != null) {
            rewardList.getItems().setAll(quest.itemRewards().stream()
                    .map(ItemReward::describe)
                    .collect(Collectors.toList()));
        }
        if (dependencyList != null) {
            dependencyList.getItems().setAll(quest.dependencies().stream()
                    .map(this::describeDependency)
                    .collect(Collectors.toList()));
        }
    }

    private String describeTask(Task task) {
        if (task == null) {
            return "Unknown task";
        }
        return task.describe();
    }

    private String describeDependency(Dependency dependency) {
        if (dependency == null) {
            return "Unknown dependency";
        }
        String suffix = dependency.required() ? "required" : "optional";
        return dependency.questId() + " (" + suffix + ")";
    }

    public Quest getQuest() {
        return quest;
    }
}
