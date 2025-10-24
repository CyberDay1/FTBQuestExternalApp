package dev.ftbq.editor.controller;

import dev.ftbq.editor.assets.CacheManager;
import dev.ftbq.editor.domain.BackgroundAlignment;
import dev.ftbq.editor.domain.BackgroundRef;
import dev.ftbq.editor.domain.BackgroundRepeat;
import dev.ftbq.editor.support.UiServiceLocator;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BackgroundImage;
import javafx.scene.layout.BackgroundPosition;
import javafx.scene.layout.BackgroundSize;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class BackgroundEditorController {
    @FXML
    private Button chooseImageButton;

    @FXML
    private ColorPicker colorPicker;

    @FXML
    private ComboBox<BackgroundAlignment> alignmentComboBox;

    @FXML
    private ComboBox<BackgroundRepeat> repeatComboBox;

    @FXML
    private Pane previewPane;

    @FXML
    private Label imagePathLabel;

    private final CacheManager cacheManager;
    private final ObjectProperty<BackgroundRef> background = new SimpleObjectProperty<>(new BackgroundRef("minecraft:textures/gui/default.png"));
    private final Map<String, javafx.scene.image.Image> imageCache = new HashMap<>();
    private boolean updatingFromModel;
    private ObjectProperty<BackgroundRef> externalBinding;

    public BackgroundEditorController() {
        this(UiServiceLocator.getCacheManager());
    }

    BackgroundEditorController(CacheManager cacheManager) {
        this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager");
    }

    @FXML
    public void initialize() {
        configureAlignmentCombo();
        configureRepeatCombo();
        if (chooseImageButton != null && chooseImageButton.getAccessibleText() == null) {
            chooseImageButton.setAccessibleText("Choose a background image");
        }
        background.addListener((obs, oldValue, newValue) -> {
            BackgroundRef next = newValue == null ? new BackgroundRef("minecraft:textures/gui/default.png") : newValue;
            updatingFromModel = true;
            try {
                refreshControls(next);
            } finally {
                updatingFromModel = false;
            }
            updatePreview(next);
        });
        refreshControls(background.get());
        updatePreview(background.get());
    }

    @FXML
    private void onChooseImage() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select background image");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image files", "*.png", "*.jpg", "*.jpeg", "*.gif"),
                new FileChooser.ExtensionFilter("All files", "*.*")
        );
        Window window = previewPane != null && previewPane.getScene() != null ? previewPane.getScene().getWindow() : null;
        File file = chooser.showOpenDialog(window);
        if (file == null) {
            return;
        }
        try {
            byte[] data = Files.readAllBytes(file.toPath());
            String hash = cacheManager.storeBackground(data);
            Path cached = cacheManager.resolveBackground(hash);
            updateBackground(current -> new BackgroundRef(
                    file.getAbsolutePath(),
                    current.relativePath(),
                    Optional.of(cached.toAbsolutePath().toString()),
                    current.colorHex(),
                    current.alignment(),
                    current.repeat()
            ));
            imageCache.remove(file.getAbsolutePath());
            imageCache.remove(cached.toAbsolutePath().toString());
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    @FXML
    private void onColorPick() {
        if (updatingFromModel) {
            return;
        }
        if (colorPicker == null) {
            return;
        }
        Color value = colorPicker.getValue();
        updateBackground(current -> new BackgroundRef(
                current.texture(),
                current.relativePath(),
                current.path(),
                value == null ? Optional.empty() : Optional.of(toHex(value)),
                current.alignment(),
                current.repeat()
        ));
    }

    public ObjectProperty<BackgroundRef> backgroundProperty() {
        return background;
    }

    public BackgroundRef getBackground() {
        return background.get();
    }

    public void setBackground(BackgroundRef backgroundRef) {
        background.set(backgroundRef == null ? new BackgroundRef("minecraft:textures/gui/default.png") : backgroundRef);
    }

    public void bindBackground(ObjectProperty<BackgroundRef> property) {
        if (externalBinding != null) {
            background.unbindBidirectional(externalBinding);
        }
        externalBinding = property;
        if (property != null) {
            background.bindBidirectional(property);
        }
    }

    private void configureAlignmentCombo() {
        if (alignmentComboBox == null) {
            return;
        }
        ObservableList<BackgroundAlignment> items = FXCollections.observableArrayList(BackgroundAlignment.values());
        alignmentComboBox.setItems(items);
        alignmentComboBox.setButtonCell(new AlignmentCell());
        alignmentComboBox.setCellFactory(list -> new AlignmentCell());
        alignmentComboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (updatingFromModel) {
                return;
            }
            updateBackground(current -> new BackgroundRef(
                    current.texture(),
                    current.relativePath(),
                    current.path(),
                    current.colorHex(),
                    Optional.ofNullable(newValue),
                    current.repeat()
            ));
        });
    }

    private void configureRepeatCombo() {
        if (repeatComboBox == null) {
            return;
        }
        ObservableList<BackgroundRepeat> items = FXCollections.observableArrayList(BackgroundRepeat.values());
        repeatComboBox.setItems(items);
        repeatComboBox.setButtonCell(new RepeatCell());
        repeatComboBox.setCellFactory(list -> new RepeatCell());
        repeatComboBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (updatingFromModel) {
                return;
            }
            updateBackground(current -> new BackgroundRef(
                    current.texture(),
                    current.relativePath(),
                    current.path(),
                    current.colorHex(),
                    current.alignment(),
                    Optional.ofNullable(newValue)
            ));
        });
    }

    private void refreshControls(BackgroundRef ref) {
        if (colorPicker != null) {
            Color fallback = ref.colorHex().map(this::parseColor).orElse(Color.web("#1e1e1e"));
            colorPicker.setValue(fallback);
        }
        if (alignmentComboBox != null) {
            alignmentComboBox.setValue(ref.alignment().orElse(null));
        }
        if (repeatComboBox != null) {
            repeatComboBox.setValue(ref.repeat().orElse(BackgroundRepeat.BOTH));
        }
        if (imagePathLabel != null) {
            imagePathLabel.setText(ref.path().orElse(ref.texture()));
        }
    }

    private void updatePreview(BackgroundRef ref) {
        if (previewPane == null) {
            return;
        }
        Color fillColor = ref.colorHex().map(this::parseColor).orElse(Color.web("#1e1e1e"));
        BackgroundFill fill = new BackgroundFill(fillColor, CornerRadii.EMPTY, Insets.EMPTY);
        Optional<javafx.scene.image.Image> image = loadImage(ref);
        if (image.isEmpty()) {
            previewPane.setBackground(new Background(fill));
            return;
        }

        javafx.scene.layout.BackgroundRepeat repeatX;
        javafx.scene.layout.BackgroundRepeat repeatY;
        BackgroundRepeat repeat = ref.repeat().orElse(BackgroundRepeat.BOTH);
        switch (repeat) {
            case NONE -> {
                repeatX = javafx.scene.layout.BackgroundRepeat.NO_REPEAT;
                repeatY = javafx.scene.layout.BackgroundRepeat.NO_REPEAT;
            }
            case HORIZONTAL -> {
                repeatX = javafx.scene.layout.BackgroundRepeat.REPEAT;
                repeatY = javafx.scene.layout.BackgroundRepeat.NO_REPEAT;
            }
            case VERTICAL -> {
                repeatX = javafx.scene.layout.BackgroundRepeat.NO_REPEAT;
                repeatY = javafx.scene.layout.BackgroundRepeat.REPEAT;
            }
            default -> {
                repeatX = javafx.scene.layout.BackgroundRepeat.REPEAT;
                repeatY = javafx.scene.layout.BackgroundRepeat.REPEAT;
            }
        }

        BackgroundPosition position = toBackgroundPosition(ref.alignment());
        BackgroundImage bgImage = new BackgroundImage(image.get(), repeatX, repeatY, position, BackgroundSize.DEFAULT);
        previewPane.setBackground(new Background(List.of(fill), List.of(bgImage)));
    }

    private Optional<javafx.scene.image.Image> loadImage(BackgroundRef ref) {
        return ref.path()
                .or(() -> Optional.ofNullable(ref.texture()))
                .filter(path -> path != null && !path.isBlank())
                .flatMap(this::fetchImage);
    }

    private Optional<javafx.scene.image.Image> fetchImage(String path) {
        if (imageCache.containsKey(path)) {
            return Optional.ofNullable(imageCache.get(path));
        }
        try {
            Path file = Path.of(path);
            if (!Files.exists(file)) {
                imageCache.put(path, null);
                return Optional.empty();
            }
            javafx.scene.image.Image image = new javafx.scene.image.Image(file.toUri().toString(), true);
            imageCache.put(path, image);
            return Optional.of(image);
        } catch (Exception ex) {
            imageCache.put(path, null);
            return Optional.empty();
        }
    }

    private void updateBackground(java.util.function.Function<BackgroundRef, BackgroundRef> updater) {
        BackgroundRef current = background.get();
        BackgroundRef updated = updater.apply(current == null ? new BackgroundRef("minecraft:textures/gui/default.png") : current);
        background.set(updated);
    }

    private Color parseColor(String value) {
        try {
            return Color.web(value);
        } catch (IllegalArgumentException ex) {
            return Color.web("#1e1e1e");
        }
    }

    private String toHex(Color color) {
        int r = (int) Math.round(color.getRed() * 255);
        int g = (int) Math.round(color.getGreen() * 255);
        int b = (int) Math.round(color.getBlue() * 255);
        return String.format("#%02X%02X%02X", r, g, b);
    }

    private BackgroundPosition toBackgroundPosition(Optional<BackgroundAlignment> alignment) {
        if (alignment.isEmpty()) {
            return BackgroundPosition.CENTER;
        }
        return switch (alignment.get()) {
            case TOP_LEFT -> new BackgroundPosition(Side.LEFT, 0, false, Side.TOP, 0, false);
            case TOP_RIGHT -> new BackgroundPosition(Side.RIGHT, 0, false, Side.TOP, 0, false);
            case BOTTOM_LEFT -> new BackgroundPosition(Side.LEFT, 0, false, Side.BOTTOM, 0, false);
            case BOTTOM_RIGHT -> new BackgroundPosition(Side.RIGHT, 0, false, Side.BOTTOM, 0, false);
            case TOP -> new BackgroundPosition(Side.LEFT, 50, true, Side.TOP, 0, false);
            case BOTTOM -> new BackgroundPosition(Side.LEFT, 50, true, Side.BOTTOM, 0, false);
            case LEFT -> new BackgroundPosition(Side.LEFT, 0, false, Side.TOP, 50, true);
            case RIGHT -> new BackgroundPosition(Side.RIGHT, 0, false, Side.TOP, 50, true);
            default -> BackgroundPosition.CENTER;
        };
    }

    private static class AlignmentCell extends ListCell<BackgroundAlignment> {
        @Override
        protected void updateItem(BackgroundAlignment item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? "Auto" : formatName(item.name()));
        }
    }

    private static class RepeatCell extends ListCell<BackgroundRepeat> {
        @Override
        protected void updateItem(BackgroundRepeat item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? "Tile Both" : formatName(item.name()));
        }
    }

    private static String formatName(String name) {
        String lower = name.replace('_', ' ').toLowerCase(Locale.ENGLISH);
        if (lower.isEmpty()) {
            return lower;
        }
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
