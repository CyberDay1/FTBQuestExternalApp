package dev.ftbq.editor.controller;

import dev.ftbq.editor.ThemeService;
import dev.ftbq.editor.assets.CacheManager;
import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.LootPool;
import dev.ftbq.editor.support.UiServiceLocator;
import dev.ftbq.editor.viewmodel.LootTableEditorViewModel;
import dev.ftbq.editor.viewmodel.LootTableEditorViewModel.LootEntryRow;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import javafx.scene.control.cell.TextFieldTableCell;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Controller for the loot table editor screen.
 */
public class LootTableEditorController {
    @FXML
    private TextField tableNameField;
    @FXML
    private Button addPoolButton;
    @FXML
    private ListView<LootPool> lootItemList;
    @FXML
    private TableView<LootEntryRow> entryTable;
    @FXML
    private TableColumn<LootEntryRow, LootEntryRow> iconColumn;
    @FXML
    private TableColumn<LootEntryRow, String> itemIdColumn;
    @FXML
    private TableColumn<LootEntryRow, String> displayNameColumn;
    @FXML
    private TableColumn<LootEntryRow, Double> weightColumn;
    @FXML
    private TableColumn<LootEntryRow, String> conditionsColumn;
    @FXML
    private Button addEntryButton;
    @FXML
    private Button removeEntryButton;
    @FXML
    private Button previewButton;
    @FXML
    private Label previewSummary;

    private final LootTableEditorViewModel viewModel;
    private final CacheManager cacheManager;
    private final ExecutorService iconExecutor = Executors.newFixedThreadPool(2);

    public LootTableEditorController() {
        this(new LootTableEditorViewModel(
                UiServiceLocator.getStoreDao(),
                UiServiceLocator.getVersionCatalog()),
                UiServiceLocator.getCacheManager());
    }

    LootTableEditorController(LootTableEditorViewModel viewModel, CacheManager cacheManager) {
        this.viewModel = Objects.requireNonNull(viewModel, "viewModel");
        this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager");
    }

    @FXML
    public void initialize() {
        tableNameField.textProperty().bindBidirectional(viewModel.tableNameProperty());
        lootItemList.setItems(viewModel.getPools());
        lootItemList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(LootPool item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });
        lootItemList.getSelectionModel().selectedItemProperty().addListener((obs, oldPool, newPool) ->
                viewModel.selectedPoolProperty().set(newPool));
        viewModel.selectedPoolProperty().addListener((obs, oldPool, newPool) -> {
            if (lootItemList.getSelectionModel().getSelectedItem() != newPool) {
                lootItemList.getSelectionModel().select(newPool);
            }
        });

        entryTable.setItems(viewModel.getEntries());
        entryTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        entryTable.setEditable(true);

        iconColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
        iconColumn.setCellFactory(column -> new IconTableCell());

        itemIdColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().itemId()));
        displayNameColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().displayName()));
        conditionsColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().conditionsSummary()));

        weightColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().weight()));
        weightColumn.setCellFactory(TextFieldTableCell.forTableColumn(new DoubleStringConverter()));
        weightColumn.setOnEditCommit(event -> {
            LootEntryRow row = event.getRowValue();
            Double newValue = event.getNewValue();
            if (row != null && newValue != null) {
                viewModel.updateEntryWeight(row, newValue);
            }
        });

        entryTable.setRowFactory(table -> new TableRow<>() {
            @Override
            protected void updateItem(LootEntryRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setStyle("");
                    setTooltip(null);
                } else if (!item.validInActiveVersion()) {
                    setStyle("-fx-background-color: rgba(255,0,0,0.15);");
                    setTooltip(new Tooltip("Item missing in current version"));
                } else {
                    setStyle("");
                    setTooltip(null);
                }
            }
        });

        addEntryButton.disableProperty().bind(viewModel.selectedPoolProperty().isNull());
        removeEntryButton.disableProperty().bind(entryTable.getSelectionModel().selectedItemProperty().isNull());
        previewButton.disableProperty().bind(viewModel.selectedPoolProperty().isNull());

        entryTable.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                dispose();
            }
        });
    }

    @FXML
    private void onAddPool() {
        LootPool newPool = viewModel.addPool();
        Platform.runLater(() -> lootItemList.getSelectionModel().select(newPool));
    }

    @FXML
    private void onAddEntry() {
        if (viewModel.selectedPoolProperty().get() == null) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/dev/ftbq/editor/view/item_browser.fxml"));
            Parent root = loader.load();
            ItemBrowserController controller = loader.getController();
            controller.setOnItemSelected(entity -> {
                if (entity != null) {
                    ItemRef ref = new ItemRef(entity.id(), 1);
                    viewModel.addEntry(ref);
                }
            });
            Stage stage = new Stage();
            stage.setTitle("Select Item");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setScene(new Scene(root));
            ThemeService.getInstance().registerStage(stage);
            stage.show();
            stage.setOnHidden(event -> controller.dispose());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onRemoveEntry() {
        List<LootEntryRow> selected = List.copyOf(entryTable.getSelectionModel().getSelectedItems());
        viewModel.removeSelectedEntries(selected);
    }

    @FXML
    private void onPreview() {
        previewSummary.setText(viewModel.computePreview());
    }

    public void focusOnTable(String tableId) {
        Platform.runLater(() -> {
            if (tableId != null && !tableId.isBlank()) {
                viewModel.tableNameProperty().set(tableId);
            }
            if (!viewModel.getPools().isEmpty()) {
                lootItemList.requestFocus();
            }
            tableNameField.requestFocus();
            tableNameField.selectAll();
        });
    }

    private void dispose() {
        iconExecutor.shutdownNow();
    }

    private class IconTableCell extends TableCell<LootEntryRow, LootEntryRow> {
        private final ImageView imageView = new ImageView();
        private CompletableFuture<Image> loadingTask;

        private IconTableCell() {
            imageView.setFitWidth(32);
            imageView.setFitHeight(32);
            imageView.setPreserveRatio(true);
        }

        @Override
        protected void updateItem(LootEntryRow item, boolean empty) {
            super.updateItem(item, empty);
            if (loadingTask != null) {
                loadingTask.cancel(true);
                loadingTask = null;
            }

            if (empty || item == null) {
                setGraphic(null);
                return;
            }

            setGraphic(imageView);
            imageView.setImage(null);

            item.iconHash().ifPresentOrElse(hash -> {
                loadingTask = CompletableFuture.supplyAsync(() -> cacheManager.fetchIcon(hash)
                        .map(bytes -> new Image(new ByteArrayInputStream(bytes)))
                        .orElse(null), iconExecutor);
                loadingTask.thenAcceptAsync(image -> {
                    if (!isEmpty() && image != null) {
                        imageView.setImage(image);
                    }
                }, Platform::runLater);
            }, () -> imageView.setImage(null));
        }
    }

    private static class DoubleStringConverter extends StringConverter<Double> {
        @Override
        public String toString(Double value) {
            if (value == null) {
                return "";
            }
            if (value == value.longValue()) {
                return Long.toString(value.longValue());
            }
            return value.toString();
        }

        @Override
        public Double fromString(String string) {
            if (string == null || string.isBlank()) {
                return 1.0;
            }
            try {
                return Double.parseDouble(string.trim());
            } catch (NumberFormatException ex) {
                return 1.0;
            }
        }
    }
}

