package dev.ftbq.editor.controller;

import dev.ftbq.editor.ThemeService;
import dev.ftbq.editor.assets.CacheManager;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.Reward;
import dev.ftbq.editor.domain.Task;
import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.services.UiServiceLocator;
import dev.ftbq.editor.services.bus.CommandBus;
import dev.ftbq.editor.services.bus.EventBus;
import dev.ftbq.editor.services.bus.ServiceLocator;
import dev.ftbq.editor.services.bus.UndoManager;
import dev.ftbq.editor.services.logging.StructuredLogger;
import dev.ftbq.editor.viewmodel.QuestEditorViewModel;
import dev.ftbq.editor.controller.ItemBrowserController;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Updated quest editor controller.
 *
 * This version enhances the icon handling by loading the actual icon image
 * from the cache into the ImageView and sets up accessible text properly.
 */
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
    private Button linkLootTableButton;

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
    private final StructuredLogger logger;

    private final Tooltip iconTooltip = new Tooltip();
    private Consumer<String> lootTableLinkHandler = tableId -> { };
    private UndoManager undoManager;

    public QuestEditorController() {
        this(
                new QuestEditorViewModel(ServiceLocator.commandBus(), ServiceLocator.eventBus(), ServiceLocator.undoManager()),
                ServiceLocator.loggerFactory().create(QuestEditorController.class)
        );
        this.undoManager = ServiceLocator.undoManager();
    }

    QuestEditorController(QuestEditorViewModel viewModel) {
        this(viewModel, ServiceLocator.loggerFactory().create(QuestEditorController.class));
    }

    QuestEditorController(QuestEditorViewModel viewModel, StructuredLogger logger) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void setCommandBus(CommandBus commandBus) {
        viewModel.setCommandBus(Objects.requireNonNull(commandBus, "commandBus"));
    }

    public void setEventBus(EventBus eventBus) {
        viewModel.setEventBus(Objects.requireNonNull(eventBus, "eventBus"));
    }

    public void setUndoManager(UndoManager undoManager) {
        this.undoManager = Objects.requireNonNull(undoManager, "undoManager");
        viewModel.setUndoManager(undoManager);
    }

    public void setLootTableLinkHandler(Consumer<String> handler) {
        lootTableLinkHandler = handler == null ? tableId -> { } : handler;
    }

    @FXML
    public void initialize() {
        logger.info("Quest editor view initialised");
        attachStylesheet();
        bindFields();
        configureListViews();
        installUndoRedoShortcuts();
        if (questDescriptionArea != null) {
            questDescriptionArea.setWrapText(true);
        }
        if (chooseIconButton != null) {
            chooseIconButton.setTooltip(iconTooltip);
            if (chooseIconButton.getAccessibleText() == null) {
                chooseIconButton.setAccessibleText("Choose quest icon");
            }
        }
        if (linkLootTableButton != null && linkLootTableButton.getAccessibleText() == null) {
            linkLootTableButton.setAccessibleText("Link loot table");
        }
        if (saveButton != null && saveButton.getAccessibleText() == null) {
            saveButton.setAccessibleText("Save quest");
        }
        if (cancelButton != null && cancelButton.getAccessibleText() == null) {
            cancelButton.setAccessibleText("Cancel quest edits");
        }
        IconRef initialIcon = viewModel.iconProperty().get();
        updateIconTooltip(initialIcon);
        viewModel.iconProperty().addListener((obs, oldIcon, newIcon) -> updateIconTooltip(newIcon));
        if (viewModel.getCurrentQuest() == null) {
            loadQuest(createDefaultQuest());
        } else {
            refreshFromViewModel();
        }
    }

    private void installUndoRedoShortcuts() {
        if (rootPane != null) {
            rootPane.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPressed);
        }
    }

    private void handleKeyPressed(KeyEvent event) {
        if (!event.isShortcutDown()) {
            return;
        }
        if (event.getCode() == KeyCode.Z) {
            if (undoManager != null && undoManager.undo()) {
                event.consume();
            }
        } else if (event.getCode() == KeyCode.Y) {
            if (undoManager != null && undoManager.redo()) {
                event.consume();
            }
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
        if (questTitleField != null) {
            questTitleField.textProperty().bindBidirectional(viewModel.titleProperty());
        }
        if (questDescriptionArea != null) {
            questDescriptionArea.textProperty().bindBidirectional(viewModel.descriptionProperty());
        }
    }

    private void configureListViews() {
        if (dependencyListView != null) {
            dependencyListView.setItems(viewModel.getDependencies());
            dependencyListView.setCellFactory(list -> new ListCell<>() {
                @Override
                protected void updateItem(Dependency item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.questId() + (item.required() ? " (required)" : ""));
                }
            });
        }

        if (taskListView != null) {
            taskListView.setItems(viewModel.getTasks());
            taskListView.setCellFactory(list -> new ListCell<>() {
                @Override
                protected void updateItem(Task item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.type());
                }
            });
        }

        if (rewardListView != null) {
            rewardListView.setItems(viewModel.getRewards());
            rewardListView.setCellFactory(list -> new ListCell<>() {
                @Override
                protected void updateItem(Reward item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.type());
                }
            });
        }
    }

    /**
     * Updates the tooltip and icon view based on the provided icon reference.
     *
     * When an icon is selected, this method fetches the corresponding icon
     * bytes from the cache using {@link CacheManager} and displays the image
     * in the {@link ImageView}. It also updates the accessible text and
     * visibility of the icon view.
     *
     * @param icon the selected icon, or null if none is selected
     */
    private void updateIconTooltip(IconRef icon) {
        if (icon == null) {
            iconTooltip.setText("No icon selected");
        } else {
            iconTooltip.setText("Icon: " + icon.icon());
        }
        if (questIconView != null) {
            if (icon == null) {
                questIconView.setImage(null);
            } else {
                CacheManager cacheManager = UiServiceLocator.cacheManager;
                if (cacheManager != null) {
                    cacheManager.fetchIcon(icon.icon()).ifPresentOrElse(
                            bytes -> questIconView.setImage(new Image(new ByteArrayInputStream(bytes))),
                            () -> questIconView.setImage(null)
                    );
                } else {
                    questIconView.setImage(null);
                }
            }
            // Update accessibility and visibility
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
        if (questTitleField != null) {
            questTitleField.positionCaret(questTitleField.getText().length());
        }
        if (questDescriptionArea != null) {
            questDescriptionArea.positionCaret(questDescriptionArea.getText().length());
        }
    }

    @FXML
    private void onChooseIconButton() {
        logger.debug("Opening item browser for quest icon selection");
        openItemBrowser();
    }

    @FXML
    private void onLinkLootTable() {
        String tableId;
        if (viewModel.getCurrentQuest() != null) {
            tableId = viewModel.getCurrentQuest().id();
        } else {
            tableId = viewModel.titleProperty().get();
        }
        logger.debug("Linking quest to loot table", StructuredLogger.field("questId", tableId));
        lootTableLinkHandler.accept(tableId == null ? "" : tableId);
    }

    @FXML
    private void handleSave() {
        Quest saved = viewModel.save();
        logger.info("Quest saved", StructuredLogger.field("questId", saved.id()));
    }

    @FXML
    private void handleCancel() {
        viewModel.revertChanges();
        logger.info("Quest edits reverted", StructuredLogger.field("questId", viewModel.getCurrentQuest() != null ? viewModel.getCurrentQuest().id() : ""));
        refreshFromViewModel();
    }

    void openItemBrowser() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/dev/ftbq/editor/view/item_browser.fxml"));
            Parent root = loader.load();
            ItemBrowserController controller = loader.getController();
            controller.setOnItemSelected(entity -> {
                if (entity != null) {
                    IconRef selectedIcon = new IconRef(entity.id());
                    viewModel.iconProperty().set(selectedIcon);
                }
            });

            Stage stage = new Stage();
            stage.setTitle("Item Browser");
            stage.setScene(new Scene(root));
            ThemeService.getInstance().registerStage(stage);
            if (rootPane != null && rootPane.getScene() != null) {
                stage.initOwner(rootPane.getScene().getWindow());
                stage.initModality(Modality.WINDOW_MODAL);
            }
            stage.setOnHidden(event -> controller.dispose());
            stage.show();
        } catch (IOException e) {
            logger.error("Failed to open item browser", e);
        }
    }
}