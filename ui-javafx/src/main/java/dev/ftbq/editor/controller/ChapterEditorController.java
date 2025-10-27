package dev.ftbq.editor.controller;

import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.Reward;
import dev.ftbq.editor.domain.Task;
import dev.ftbq.editor.view.graph.GraphCanvas;
import dev.ftbq.editor.viewmodel.ChapterEditorViewModel;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.SelectionMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Controller responsible for presenting a quest chapter graph.
 */
public class ChapterEditorController {

    @FXML
    private StackPane graphContainer;

    @FXML
    private Label chapterTitleLabel;

    @FXML
    private VBox questDetailBox;

    @FXML
    private ListView<String> taskList;

    @FXML
    private ListView<String> rewardList;

    @FXML
    private ListView<String> dependencyList;

    @FXML
    private BorderPane questEditorPane;

    @FXML
    private Label questEditorTitleLabel;

    @FXML
    private TextField questTitleField;

    @FXML
    private TextArea questDescriptionArea;

    @FXML
    private TextField questIconField;

    @FXML
    private ListView<String> questDependencyList;

    @FXML
    private ListView<String> questTaskList;

    @FXML
    private ListView<String> questRewardList;

    private final GraphCanvas graphCanvas = new GraphCanvas();
    private final ObservableList<String> tasks = FXCollections.observableArrayList();
    private final ObservableList<String> rewards = FXCollections.observableArrayList();
    private final ObservableList<String> dependencies = FXCollections.observableArrayList();
    private final ObservableList<String> questDependencies = FXCollections.observableArrayList();
    private final ObservableList<String> questTasks = FXCollections.observableArrayList();
    private final ObservableList<String> questRewards = FXCollections.observableArrayList();
    private int taskCounter = 1;
    private int rewardCounter = 1;
    private int dependencyCounter = 1;
    private ChapterEditorViewModel viewModel;
    private Quest activeQuest;

    public ChapterEditorController() {
        graphCanvas.setQuestEditHandler(this::showQuestEditor);
    }

    @FXML
    public void initialize() {
        if (graphContainer != null) {
            graphCanvas.setMinSize(0, 0);
            graphCanvas.setPrefSize(StackPane.USE_COMPUTED_SIZE, StackPane.USE_COMPUTED_SIZE);
            graphCanvas.prefWidthProperty().bind(graphContainer.widthProperty());
            graphCanvas.prefHeightProperty().bind(graphContainer.heightProperty());
            graphContainer.getChildren().add(graphCanvas);
        }
        graphCanvas.rebuildGraph();
        configureDetailLists();
        configureQuestEditorLists();
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
            clearQuestDetails();
            closeQuestEditor();
            return;
        }
        if (chapterTitleLabel != null) {
            chapterTitleLabel.setText(chapter.title());
        }
        graphCanvas.setChapter(chapter);
        graphCanvas.rebuildGraph();
        populateQuestDetails(chapter);
        refreshActiveQuest(chapter);
    }

    private void configureDetailLists() {
        configureListView(taskList, tasks, "No tasks defined");
        configureListView(rewardList, rewards, "No rewards defined");
        configureListView(dependencyList, dependencies, "No dependencies defined");
    }

    private void configureQuestEditorLists() {
        configureReadOnlyListView(questDependencyList, questDependencies, "No dependencies");
        configureReadOnlyListView(questTaskList, questTasks, "No tasks");
        configureReadOnlyListView(questRewardList, questRewards, "No rewards");
    }

    private void configureListView(ListView<String> listView, ObservableList<String> items, String placeholderText) {
        if (listView == null) {
            return;
        }
        listView.setItems(items);
        listView.setPlaceholder(new Label(placeholderText));
        listView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        listView.setOnKeyPressed(event -> {
            if ((event.getCode() == KeyCode.DELETE || event.getCode() == KeyCode.BACK_SPACE)
                    && !listView.getSelectionModel().getSelectedItems().isEmpty()) {
                List<String> selected = List.copyOf(listView.getSelectionModel().getSelectedItems());
                items.removeAll(selected);
                event.consume();
            }
        });
    }

    private void configureReadOnlyListView(ListView<String> listView, ObservableList<String> items, String placeholderText) {
        if (listView == null) {
            return;
        }
        listView.setItems(items);
        listView.setPlaceholder(new Label(placeholderText));
        listView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        listView.setFocusTraversable(false);
    }

    private void clearQuestDetails() {
        tasks.clear();
        rewards.clear();
        dependencies.clear();
        taskCounter = 1;
        rewardCounter = 1;
        dependencyCounter = 1;
    }

    private void populateQuestDetails(Chapter chapter) {
        clearQuestDetails();
        if (chapter == null || chapter.quests() == null) {
            return;
        }
        for (Quest quest : chapter.quests()) {
            if (quest == null) {
                continue;
            }
            for (Task task : quest.tasks()) {
                tasks.add(formatTaskEntry(quest, task));
            }
            for (Reward reward : quest.rewards()) {
                rewards.add(formatRewardEntry(quest, reward));
            }
            for (Dependency dependency : quest.dependencies()) {
                dependencies.add(formatDependencyEntry(quest, dependency));
            }
        }
        taskCounter = tasks.size() + 1;
        rewardCounter = rewards.size() + 1;
        dependencyCounter = dependencies.size() + 1;
    }

    private String formatTaskEntry(Quest quest, Task task) {
        return "%s: %s".formatted(quest.title(), task.type());
    }

    private String formatRewardEntry(Quest quest, Reward reward) {
        return "%s: %s".formatted(quest.title(), reward.type());
    }

    private String formatDependencyEntry(Quest quest, Dependency dependency) {
        String requirement = dependency.required() ? "required" : "optional";
        return "%s → %s (%s)".formatted(quest.title(), dependency.questId(), requirement);
    }

    private void refreshActiveQuest(Chapter chapter) {
        if (activeQuest == null) {
            return;
        }
        if (chapter == null) {
            closeQuestEditor();
            return;
        }
        Optional<Quest> updatedQuest = chapter.quests().stream()
                .filter(quest -> quest.id().equals(activeQuest.id()))
                .findFirst();
        if (updatedQuest.isPresent()) {
            activeQuest = updatedQuest.get();
            updateQuestEditorFields(activeQuest);
        } else {
            closeQuestEditor();
        }
    }

    @FXML
    private void onAddTask() {
        tasks.add("Task %d".formatted(taskCounter++));
    }

    @FXML
    private void onAddReward() {
        rewards.add("Reward %d".formatted(rewardCounter++));
    }

    @FXML
    private void onAddDependency() {
        dependencies.add("Dependency %d".formatted(dependencyCounter++));
    }

    private void showQuestEditor(Quest quest) {
        activeQuest = quest;
        updateQuestEditorFields(quest);
        if (questEditorPane != null && questDetailBox != null) {
            questEditorPane.setVisible(true);
            questEditorPane.setManaged(true);
            questDetailBox.setVisible(false);
            questDetailBox.setManaged(false);
        }
    }

    private void updateQuestEditorFields(Quest quest) {
        if (quest == null) {
            return;
        }
        if (questEditorTitleLabel != null) {
            questEditorTitleLabel.setText("Edit Quest: " + quest.title());
        }
        if (questTitleField != null) {
            questTitleField.setText(quest.title());
        }
        if (questDescriptionArea != null) {
            questDescriptionArea.setText(quest.description());
        }
        if (questIconField != null && quest.icon() != null) {
            questIconField.setText(quest.icon().icon());
        }
        questDependencies.setAll(quest.dependencies().stream()
                .map(dependency -> "%s (%s)".formatted(
                        dependency.questId(),
                        dependency.required() ? "required" : "optional"))
                .toList());
        questTasks.setAll(quest.tasks().stream()
                .map(task -> "%s".formatted(task.type()))
                .toList());
        questRewards.setAll(quest.rewards().stream()
                .map(reward -> "%s".formatted(reward.type()))
                .toList());
    }

    @FXML
    private void onCloseQuestEditor() {
        closeQuestEditor();
    }

    @FXML
    private void onSaveQuest() {
        if (activeQuest == null || viewModel == null) {
            return;
        }
        String title = questTitleField == null ? activeQuest.title() : questTitleField.getText().trim();
        if (title.isEmpty()) {
            title = activeQuest.title();
        }
        String description = questDescriptionArea == null ? activeQuest.description() : questDescriptionArea.getText();
        String icon = questIconField == null ? activeQuest.icon().icon() : questIconField.getText().trim();
        if (icon.isEmpty()) {
            icon = activeQuest.icon().icon();
        }

        Quest updatedQuest = Quest.builder()
                .id(activeQuest.id())
                .title(title)
                .description(description)
                .icon(new IconRef(icon))
                .visibility(activeQuest.visibility())
                .tasks(activeQuest.tasks())
                .rewards(activeQuest.rewards())
                .dependencies(activeQuest.dependencies())
                .build();

        viewModel.updateQuest(updatedQuest);
    }

    private void closeQuestEditor() {
        activeQuest = null;
        if (questEditorPane != null) {
            questEditorPane.setVisible(false);
            questEditorPane.setManaged(false);
        }
        if (questDetailBox != null) {
            questDetailBox.setVisible(true);
            questDetailBox.setManaged(true);
        }
        questDependencies.clear();
        questTasks.clear();
        questRewards.clear();
        if (questTitleField != null) {
            questTitleField.clear();
        }
        if (questDescriptionArea != null) {
            questDescriptionArea.clear();
        }
        if (questIconField != null) {
            questIconField.clear();
        }
        if (questEditorTitleLabel != null) {
            questEditorTitleLabel.setText("Quest Editor");
        }
    }
}
