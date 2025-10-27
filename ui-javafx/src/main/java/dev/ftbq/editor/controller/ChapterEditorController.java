package dev.ftbq.editor.controller;

import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.Reward;
import dev.ftbq.editor.domain.Task;
import dev.ftbq.editor.services.UiServiceLocator;
import dev.ftbq.editor.store.StoreDao;
import dev.ftbq.editor.view.graph.GraphCanvas;
import dev.ftbq.editor.viewmodel.ChapterEditorViewModel;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controller responsible for presenting a quest chapter graph.
 */
public class ChapterEditorController {

    private static final String EMPTY_TITLE = "";

    @FXML
    private StackPane graphContainer;

    @FXML
    private ComboBox<Chapter> chapterSelector;

    @FXML
    private Label chapterTitleLabel;

    @FXML
    private ListView<String> taskList;

    @FXML
    private ListView<String> rewardList;

    @FXML
    private ListView<String> dependencyList;

    private final GraphCanvas graphCanvas = new GraphCanvas();
    private final ObservableList<String> tasks = FXCollections.observableArrayList();
    private final ObservableList<String> rewards = FXCollections.observableArrayList();
    private final ObservableList<String> dependencies = FXCollections.observableArrayList();
    private final StringConverter<Chapter> chapterStringConverter = new StringConverter<>() {
        @Override
        public String toString(Chapter chapter) {
            return chapter != null ? chapter.title() : EMPTY_TITLE;
        }

        @Override
        public Chapter fromString(String string) {
            if (string == null || string.isBlank() || chapterSelector == null) {
                return null;
            }
            Optional<Chapter> match = chapterSelector.getItems().stream()
                    .filter(chapter -> string.equals(chapter.title()))
                    .findFirst();
            return match.orElse(null);
        }
    };
    private int taskCounter = 1;
    private int rewardCounter = 1;
    private int dependencyCounter = 1;
    private ChapterEditorViewModel viewModel;
    private boolean programmaticChapterSelection;
    private Chapter currentChapter;
    private String pendingSelectionQuestId;

    @FXML
    public void initialize() {
        if (graphContainer != null) {
            graphCanvas.setMinSize(0, 0);
            graphCanvas.setPrefSize(StackPane.USE_COMPUTED_SIZE, StackPane.USE_COMPUTED_SIZE);
            graphCanvas.prefWidthProperty().bind(graphContainer.widthProperty());
            graphCanvas.prefHeightProperty().bind(graphContainer.heightProperty());
            graphContainer.getChildren().add(graphCanvas);
        }
        graphCanvas.setOnNodeDoubleClick(node -> {
            Quest quest = node.getQuest();
            showQuestEditDialog(quest);
        });
        graphCanvas.rebuildGraph();
        configureChapterSelector();
        configureDetailLists();
        if (viewModel != null && viewModel.getChapter() != null) {
            applyChapter(viewModel.getChapter());
        }
    }

    public void setViewModel(ChapterEditorViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.viewModel.chapterProperty().addListener((obs, oldChapter, newChapter) -> applyChapter(newChapter));
        if (chapterSelector != null) {
            chapterSelector.setItems(viewModel.getChapters());
            selectChapter(viewModel.getChapter());
        }
        if (newChapterAvailable()) {
            applyChapter(viewModel.getChapter());
        }
    }

    private boolean newChapterAvailable() {
        return viewModel != null && viewModel.getChapter() != null;
    }

    private void applyChapter(Chapter chapter) {
        if (chapter == null) {
            currentChapter = null;
            graphCanvas.setChapter(null);
            if (chapterTitleLabel != null) {
                chapterTitleLabel.setText(EMPTY_TITLE);
            }
            selectChapter(null);
            clearQuestDetails();
            return;
        }
        currentChapter = chapter;
        if (chapterTitleLabel != null) {
            chapterTitleLabel.setText(chapter.title());
        }
        selectChapter(chapter);
        graphCanvas.setChapter(chapter);
        graphCanvas.rebuildGraph();
        populateQuestDetails(chapter);
        if (pendingSelectionQuestId != null) {
            graphCanvas.selectQuest(pendingSelectionQuestId);
            pendingSelectionQuestId = null;
        }
    }

    private void configureChapterSelector() {
        if (chapterSelector == null) {
            return;
        }
        chapterSelector.setConverter(chapterStringConverter);
        chapterSelector.setCellFactory(listView -> new ChapterListCell());
        chapterSelector.setButtonCell(new ChapterListCell());
    }

    private void selectChapter(Chapter chapter) {
        if (chapterSelector == null) {
            return;
        }
        try {
            programmaticChapterSelection = true;
            if (chapter == null) {
                chapterSelector.getSelectionModel().clearSelection();
                chapterSelector.setValue(null);
            } else {
                chapterSelector.getSelectionModel().select(chapter);
            }
        } finally {
            programmaticChapterSelection = false;
        }
    }

    private void configureDetailLists() {
        configureListView(taskList, tasks, "No tasks defined");
        configureListView(rewardList, rewards, "No rewards defined");
        configureListView(dependencyList, dependencies, "No dependencies defined");
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

    @FXML
    private void onChapterSelected() {
        if (chapterSelector == null || programmaticChapterSelection || viewModel == null) {
            return;
        }
        Chapter selected = chapterSelector.getSelectionModel().getSelectedItem();
        if (selected != null && !selected.equals(viewModel.getChapter())) {
            viewModel.loadChapter(selected);
        }
    }

    private static final class ChapterListCell extends javafx.scene.control.ListCell<Chapter> {
        @Override
        protected void updateItem(Chapter chapter, boolean empty) {
            super.updateItem(chapter, empty);
            if (empty || chapter == null) {
                setText(EMPTY_TITLE);
            } else {
                setText(chapter.title());
            }
        }
    }

    private void onAddQuest() {
        if (currentChapter == null) {
            return;
        }

        Quest newQuest = Quest.builder()
                .id(generateQuestId(currentChapter))
                .title(generateQuestTitle(currentChapter))
                .build();

        List<Quest> updatedQuests = new ArrayList<>(currentChapter.quests());
        updatedQuests.add(newQuest);

        Chapter updatedChapter = rebuildChapter(currentChapter, updatedQuests);
        pendingSelectionQuestId = newQuest.id();
        applyUpdatedChapter(updatedChapter);

        StoreDao dao = UiServiceLocator.storeDao;
        if (dao != null) {
            dao.saveQuest(newQuest);
        }
    }

    @FXML
    private void onRemoveQuest() {
        if (currentChapter == null) {
            return;
        }
        Quest selectedQuest = graphCanvas.getSelectedQuest();
        if (selectedQuest == null) {
            return;
        }

        List<Quest> updatedQuests = new ArrayList<>();
        List<Quest> questsToPersist = new ArrayList<>();
        for (Quest quest : currentChapter.quests()) {
            if (quest.id().equals(selectedQuest.id())) {
                continue;
            }
            Quest cleaned = removeDependencyReferences(quest, selectedQuest.id());
            updatedQuests.add(cleaned);
            if (cleaned != quest) {
                questsToPersist.add(cleaned);
            }
        }

        Chapter updatedChapter = rebuildChapter(currentChapter, updatedQuests);
        graphCanvas.clearSelection();
        applyUpdatedChapter(updatedChapter);

        StoreDao dao = UiServiceLocator.storeDao;
        if (dao != null) {
            dao.deleteQuest(selectedQuest.id());
            questsToPersist.forEach(dao::saveQuest);
        }
    }

    private void showQuestEditDialog(Quest quest) {
        if (quest == null) {
            return;
        }

        Dialog<Quest> dialog = new Dialog<>();
        dialog.setTitle("Edit Quest");
        dialog.setHeaderText("Update quest details");

        ButtonType saveButtonType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveButtonType, ButtonType.CANCEL);

        TextField titleField = new TextField(quest.title());
        TextArea descriptionArea = new TextArea(quest.description());
        descriptionArea.setPrefRowCount(5);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        grid.add(new Label("Title"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Description"), 0, 1);
        grid.add(descriptionArea, 1, 1);

        dialog.getDialogPane().setContent(grid);

        Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.setDisable(titleField.getText() == null || titleField.getText().isBlank());
        titleField.textProperty().addListener((obs, oldValue, newValue) ->
                saveButton.setDisable(newValue == null || newValue.isBlank()));

        dialog.setResultConverter(button -> {
            if (button == saveButtonType) {
                String title = titleField.getText() == null ? "" : titleField.getText().trim();
                String description = descriptionArea.getText() == null ? "" : descriptionArea.getText().trim();
                return Quest.builder()
                        .id(quest.id())
                        .title(title)
                        .description(description)
                        .icon(quest.icon())
                        .visibility(quest.visibility())
                        .tasks(quest.tasks())
                        .rewards(quest.rewards())
                        .dependencies(quest.dependencies())
                        .build();
            }
            return null;
        });

        Optional<Quest> result = dialog.showAndWait();
        result.ifPresent(this::saveEditedQuest);
    }

    private void saveEditedQuest(Quest updatedQuest) {
        if (currentChapter == null || updatedQuest == null) {
            return;
        }

        List<Quest> quests = new ArrayList<>(currentChapter.quests().size());
        boolean replaced = false;
        for (Quest quest : currentChapter.quests()) {
            if (quest.id().equals(updatedQuest.id())) {
                quests.add(updatedQuest);
                replaced = true;
            } else {
                quests.add(quest);
            }
        }

        if (!replaced) {
            quests.add(updatedQuest);
        }

        Chapter updatedChapter = rebuildChapter(currentChapter, quests);
        applyUpdatedChapter(updatedChapter);
        graphCanvas.selectQuest(updatedQuest.id());

        StoreDao dao = UiServiceLocator.storeDao;
        if (dao != null) {
            dao.saveQuest(updatedQuest);
        }
    }

    private Chapter rebuildChapter(Chapter original, List<Quest> quests) {
        return Chapter.builder()
                .id(original.id())
                .title(original.title())
                .icon(original.icon())
                .background(original.background())
                .visibility(original.visibility())
                .quests(quests)
                .build();
    }

    private void applyUpdatedChapter(Chapter updatedChapter) {
        if (viewModel != null) {
            viewModel.setChapter(updatedChapter);
        } else {
            applyChapter(updatedChapter);
        }
    }

    private String generateQuestId(Chapter chapter) {
        Set<String> existingIds = chapter.quests().stream()
                .map(Quest::id)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        String baseId = "new_quest";
        if (!existingIds.contains(baseId)) {
            return baseId;
        }
        int counter = 2;
        while (true) {
            String candidate = "%s_%d".formatted(baseId, counter);
            if (!existingIds.contains(candidate)) {
                return candidate;
            }
            counter++;
        }
    }

    private String generateQuestTitle(Chapter chapter) {
        Set<String> existingTitles = chapter.quests().stream()
                .map(Quest::title)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));
        String baseTitle = "New Quest";
        if (!existingTitles.contains(baseTitle)) {
            return baseTitle;
        }
        int counter = 2;
        while (true) {
            String candidate = "%s %d".formatted(baseTitle, counter);
            if (!existingTitles.contains(candidate)) {
                return candidate;
            }
            counter++;
        }
    }

    private Quest removeDependencyReferences(Quest quest, String questId) {
        List<Dependency> filteredDependencies = quest.dependencies().stream()
                .filter(dependency -> !Objects.equals(dependency.questId(), questId))
                .toList();
        if (filteredDependencies.size() == quest.dependencies().size()) {
            return quest;
        }
        return Quest.builder()
                .id(quest.id())
                .title(quest.title())
                .description(quest.description())
                .icon(quest.icon())
                .visibility(quest.visibility())
                .tasks(quest.tasks())
                .rewards(quest.rewards())
                .dependencies(filteredDependencies)
                .build();
    }
}
