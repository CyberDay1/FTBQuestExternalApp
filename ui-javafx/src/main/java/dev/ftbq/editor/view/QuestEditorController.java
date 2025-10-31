package dev.ftbq.editor.view;

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
    private TextField questTitleField;

    @FXML
    private TextArea questDescriptionArea;

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
    private ListView<String> tasksListView;

    @FXML
    private ListView<String> rewardsListView;

    @FXML
    private ListView<String> dependenciesListView;

    private Quest quest;

    @FXML
    private void initialize() {
        questDescriptionArea.setWrapText(true);
        questDescriptionArea.setEditable(false);
        questTitleField.setEditable(false);
        iconIdField.setEditable(false);
        iconPathField.setEditable(false);
        visibilityField.setEditable(false);
        xpAmountField.setEditable(false);
        xpLevelsField.setEditable(false);
        lootTableField.setEditable(false);
        commandField.setEditable(false);
        commandRunAsField.setEditable(false);
        tasksListView.setItems(FXCollections.observableArrayList());
        rewardsListView.setItems(FXCollections.observableArrayList());
        dependenciesListView.setItems(FXCollections.observableArrayList());
    }

    public void setQuest(Quest quest) {
        this.quest = Objects.requireNonNull(quest, "quest");
        questIdLabel.setText(quest.id());
        questTitleField.setText(quest.title());
        questDescriptionArea.setText(quest.description());
        IconRef icon = quest.icon();
        iconIdField.setText(icon.icon());
        iconPathField.setText(icon.relativePath().orElse(""));
        visibilityField.setText(quest.visibility().name());
        xpAmountField.setText(quest.experienceAmountOptional().map(String::valueOf).orElse(""));
        xpLevelsField.setText(quest.experienceLevelsOptional().map(String::valueOf).orElse(""));
        lootTableField.setText(quest.lootTableIdOptional().orElse(""));
        RewardCommand commandReward = quest.commandReward();
        if (commandReward != null) {
            commandField.setText(commandReward.command());
            commandRunAsField.setText(commandReward.runAsServer() ? "Server" : "Player");
        } else {
            commandField.setText("");
            commandRunAsField.setText("");
        }
        tasksListView.getItems().setAll(quest.tasks().stream()
                .map(this::describeTask)
                .collect(Collectors.toList()));
        rewardsListView.getItems().setAll(quest.itemRewards().stream()
                .map(ItemReward::describe)
                .collect(Collectors.toList()));
        dependenciesListView.getItems().setAll(quest.dependencies().stream()
                .map(this::describeDependency)
                .collect(Collectors.toList()));
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
