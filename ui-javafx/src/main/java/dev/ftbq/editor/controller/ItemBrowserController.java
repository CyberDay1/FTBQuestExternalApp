package dev.ftbq.editor.controller;

import dev.ftbq.editor.assets.CacheManager;
import dev.ftbq.editor.store.StoreDao;
import dev.ftbq.editor.services.UiServiceLocator;
import dev.ftbq.editor.viewmodel.ItemBrowserViewModel;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class ItemBrowserController {
    @FXML
    private TextField searchField;
    @FXML
    private ComboBox<ItemBrowserViewModel.ModOption> modFilterBox;
    @FXML
    private ComboBox<String> tagFilterBox;
    @FXML
    private ComboBox<String> kindFilterBox;
    @FXML
    private CheckBox vanillaOnlyCheck;
    @FXML
    private ComboBox<StoreDao.SortMode> sortModeBox;
    @FXML
    private TableView<ItemBrowserViewModel.ItemRow> itemTable;
    @FXML
    private TableColumn<ItemBrowserViewModel.ItemRow, ItemBrowserViewModel.ItemRow> iconColumn;
    @FXML
    private TableColumn<ItemBrowserViewModel.ItemRow, String> idColumn;
    @FXML
    private TableColumn<ItemBrowserViewModel.ItemRow, String> displayNameColumn;
    @FXML
    private TableColumn<ItemBrowserViewModel.ItemRow, String> modColumn;
    @FXML
    private TableColumn<ItemBrowserViewModel.ItemRow, String> kindColumn;
    @FXML
    private Button selectButton;
    @FXML
    private Button cancelButton;

    private final ItemBrowserViewModel viewModel = new ItemBrowserViewModel(UiServiceLocator.getStoreDao());
    private final CacheManager cacheManager = UiServiceLocator.getCacheManager();
    private final ExecutorService iconExecutor = Executors.newFixedThreadPool(2);

    private Consumer<StoreDao.ItemEntity> selectionHandler;

    @FXML
    public void initialize() {
        setupTable();
        setupFilters();
        setupListeners();
        viewModel.loadFilterOptions();
        populateFilterBoxes();
        viewModel.refresh();
        if (itemTable != null) {
            itemTable.setItems(viewModel.getItems());
        }
        if (selectButton != null && itemTable != null) {
            selectButton.disableProperty().bind(itemTable.getSelectionModel().selectedItemProperty().isNull());
        }
        if (cancelButton != null && cancelButton.getAccessibleText() == null) {
            cancelButton.setAccessibleText("Cancel item selection");
        }
    }

    private void setupTable() {
        if (itemTable == null || iconColumn == null || idColumn == null || displayNameColumn == null
                || modColumn == null || kindColumn == null) {
            return;
        }
        iconColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
        iconColumn.setCellFactory(column -> new IconTableCell());

        idColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().id()));
        displayNameColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(
                data.getValue().displayName() == null ? "" : data.getValue().displayName()));
        modColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue().modDisplay()));
        kindColumn.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(
                data.getValue().kind() == null ? "" : data.getValue().kind()));
    }

    private void setupFilters() {
        if (sortModeBox == null) {
            return;
        }
        sortModeBox.getItems().setAll(StoreDao.SortMode.values());
        sortModeBox.getSelectionModel().select(StoreDao.SortMode.NAME);
    }

    private void populateFilterBoxes() {
        if (modFilterBox == null || tagFilterBox == null || kindFilterBox == null) {
            return;
        }
        ObservableList<ItemBrowserViewModel.ModOption> mods = viewModel.getAvailableMods();
        modFilterBox.getItems().setAll(mods);
        modFilterBox.setButtonCell(new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(ItemBrowserViewModel.ModOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "All Mods" : item.label());
            }
        });
        modFilterBox.setCellFactory(listView -> new javafx.scene.control.ListCell<>() {
            @Override
            protected void updateItem(ItemBrowserViewModel.ModOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "All Mods" : item.label());
            }
        });
        modFilterBox.getSelectionModel().clearSelection();

        tagFilterBox.getItems().setAll(viewModel.getAvailableTags());
        tagFilterBox.getSelectionModel().clearSelection();
        kindFilterBox.getItems().setAll(viewModel.getAvailableKinds());
        kindFilterBox.getSelectionModel().clearSelection();
    }

    private void setupListeners() {
        if (searchField == null || modFilterBox == null || tagFilterBox == null || kindFilterBox == null
                || vanillaOnlyCheck == null || sortModeBox == null) {
            return;
        }
        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            viewModel.searchTextProperty().set(newValue);
            viewModel.refresh();
        });
        modFilterBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            viewModel.selectedModProperty().set(newValue);
            viewModel.refresh();
        });
        tagFilterBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            viewModel.selectedTagProperty().set(newValue);
            viewModel.refresh();
        });
        kindFilterBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            viewModel.selectedKindProperty().set(newValue);
            viewModel.refresh();
        });
        vanillaOnlyCheck.selectedProperty().addListener((obs, oldValue, newValue) -> {
            viewModel.vanillaOnlyProperty().set(newValue);
            viewModel.refresh();
        });
        sortModeBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            viewModel.sortModeProperty().set(newValue);
            viewModel.refresh();
        });
    }

    public void setOnItemSelected(Consumer<StoreDao.ItemEntity> handler) {
        this.selectionHandler = handler;
    }

    @FXML
    private void handleSelect() {
        if (itemTable == null) {
            closeWindow();
            return;
        }
        ItemBrowserViewModel.ItemRow selected = itemTable.getSelectionModel().getSelectedItem();
        if (selected != null && selectionHandler != null) {
            selectionHandler.accept(selected.entity());
        }
        closeWindow();
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    public void dispose() {
        iconExecutor.shutdownNow();
    }

    private void closeWindow() {
        if (cancelButton == null || cancelButton.getScene() == null) {
            return;
        }
        Window window = cancelButton.getScene().getWindow();
        if (!(window instanceof Stage stage)) {
            return;
        }
        dispose();
        stage.close();
    }

    private class IconTableCell extends TableCell<ItemBrowserViewModel.ItemRow, ItemBrowserViewModel.ItemRow> {
        private final ImageView imageView = new ImageView();
        private CompletableFuture<Image> loadingTask;

        private IconTableCell() {
            imageView.setFitWidth(32);
            imageView.setFitHeight(32);
            imageView.setPreserveRatio(true);
        }

        @Override
        protected void updateItem(ItemBrowserViewModel.ItemRow item, boolean empty) {
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
            String iconHash = item.iconHash();
            if (iconHash == null || iconHash.isBlank()) {
                return;
            }

            loadingTask = CompletableFuture.supplyAsync(() -> fetchIcon(iconHash), iconExecutor)
                    .thenApply(optionalImage -> optionalImage.orElse(null));

            loadingTask.whenComplete((image, error) -> Platform.runLater(() -> {
                if (error != null || image == null) {
                    imageView.setImage(null);
                } else if (!isEmpty()) {
                    imageView.setImage(image);
                }
            }));
        }

        private Optional<Image> fetchIcon(String hash) {
            return cacheManager.fetchIcon(hash).map(bytes -> new Image(new ByteArrayInputStream(bytes)));
        }
    }
}


