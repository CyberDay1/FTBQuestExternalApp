package dev.ftbq.editor.controller;

import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.Reward;
import dev.ftbq.editor.domain.Task;
import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.services.CommandBus;
import dev.ftbq.editor.viewmodel.QuestEditorViewModel;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;

import java.util.Objects;

public class QuestEditorController {
    @FXML
    private BorderPane rootPane;

    @FXML
    private TextField questTitleField;

    @FXML
    private TextArea questDescriptionArea;

    @FXML
    private Button chooseIconButton;

    @FXML
    private ImageView questIconView;

    @FXML
    private ListView<Dependency> dependencyListView;

    @FXML
    private ListView<Task> taskListView;

    @FXML
    private ListView<Reward> rewardListView;

    @FXML
    private Button saveButton;

    @FXML
    private Button cancelButton;

    private final QuestEditorViewModel viewModel;

    private final Tooltip iconTooltip = new Tooltip();

    public QuestEditorController() {
        this(new QuestEditorViewModel(CommandBus.noop()));
    }

    QuestEditorController(QuestEditorViewModel viewModel) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
    }

    public void setCommandBus(CommandBus commandBus) {
        viewModel.setCommandBus(Objects.requireNonNull(commandBus, "commandBus"));
    }

    @FXML
    public void initialize() {
        System.out.println("QuestEditorController.initialize()");
        attachStylesheet();
        bindFields();
        configureListViews();
        questDescriptionArea.setWrapText(true);
        chooseIconButton.setTooltip(iconTooltip);
        IconRef initialIcon = viewModel.iconProperty().get();
        updateIconTooltip(initialIcon);
        viewModel.iconProperty().addListener((obs, oldIcon, newIcon) -> updateIconTooltip(newIcon));
        if (viewModel.getCurrentQuest() == null) {
            loadQuest(createDefaultQuest());
        } else {
            refreshFromViewModel();
        }
    }

    private void attachStylesheet() {
        if (rootPane != null) {
            rootPane.getStylesheets().add(
                    Objects.requireNonNull(
                            getClass().getResource("/dev/ftbq/editor/view/quest_editor.css"),
                            "quest_editor.css must be available"
                    ).toExternalForm()
            );
        }
    }

    private void bindFields() {
        questTitleField.textProperty().bindBidirectional(viewModel.titleProperty());
        questDescriptionArea.textProperty().bindBidirectional(viewModel.descriptionProperty());
    }

    private void configureListViews() {
        dependencyListView.setItems(viewModel.getDependencies());
        dependencyListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Dependency item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.questId() + (item.required() ? " (required)" : ""));
            }
        });

        taskListView.setItems(viewModel.getTasks());
        taskListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Task item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.type());
            }
        });

        rewardListView.setItems(viewModel.getRewards());
        rewardListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Reward item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.type());
            }
        });
    }

    private void updateIconTooltip(IconRef icon) {
        if (icon == null) {
            iconTooltip.setText("No icon selected");
        } else {
            iconTooltip.setText("Icon: " + icon.icon());
        }
        if (questIconView != null) {
            questIconView.setAccessibleText(icon == null ? "No icon selected" : icon.icon());
            questIconView.setManaged(icon != null);
            questIconView.setVisible(icon != null);
        }
    }

    private Quest createDefaultQuest() {
        return Quest.builder()
                .id("quest-" + System.currentTimeMillis())
                .title("New Quest")
                .description("")
                .icon(new IconRef("minecraft:book"))
                .visibility(Visibility.VISIBLE)
                .build();
    }

    public void loadQuest(Quest quest) {
        if (viewModel != null) {
            viewModel.loadQuest(quest);
            refreshFromViewModel();
        }
    }

    private void refreshFromViewModel() {
        questTitleField.positionCaret(questTitleField.getText().length());
        questDescriptionArea.positionCaret(questDescriptionArea.getText().length());
    }

    @FXML
    private void onChooseIconButton() {
        System.out.println("Item browser will open here (06-05)");
        openItemBrowser();
    }

    @FXML
    private void handleSave() {
        viewModel.save();
    }

    @FXML
    private void handleCancel() {
        viewModel.revertChanges();
        refreshFromViewModel();
    }

    void openItemBrowser() {
        /* will open item browser dialog implemented in 06-05 */
    }
}
