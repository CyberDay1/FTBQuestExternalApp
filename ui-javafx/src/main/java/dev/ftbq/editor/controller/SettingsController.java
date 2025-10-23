package dev.ftbq.editor.controller;

import dev.ftbq.editor.assets.CacheManager;
import dev.ftbq.editor.domain.version.MinecraftVersion;
import dev.ftbq.editor.domain.version.VersionCatalog;
import dev.ftbq.editor.ingest.ItemCatalogExtractor;
import dev.ftbq.editor.ingest.JarScanner;
import dev.ftbq.editor.services.catalog.CatalogImportService;
import dev.ftbq.editor.store.StoreDao;
import dev.ftbq.editor.support.UiServiceLocator;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Controller for the application settings view.
 */
public class SettingsController {
    private static final Logger LOGGER = Logger.getLogger(SettingsController.class.getName());

    @FXML
    private ComboBox<MinecraftVersion> versionBox;
    @FXML
    private Button addJarButton;
    @FXML
    private Button clearIconCacheButton;
    @FXML
    private Label statusLabel;

    private VersionCatalog versionCatalog;
    private final CacheManager cacheManager;
    private final CatalogImportService catalogImportService;

    public SettingsController() {
        this(
                UiServiceLocator.getVersionCatalog(),
                UiServiceLocator.getCacheManager(),
                UiServiceLocator.getStoreDao());
    }

    SettingsController(VersionCatalog versionCatalog, CacheManager cacheManager, StoreDao storeDao) {
        this.versionCatalog = Objects.requireNonNull(versionCatalog, "versionCatalog");
        this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager");
        this.catalogImportService = new CatalogImportService(Objects.requireNonNull(storeDao, "storeDao"));
    }

    @FXML
    public void initialize() {
        versionBox.getItems().setAll(Arrays.asList(MinecraftVersion.values()));
        versionBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVersion, newVersion) -> {
            if (newVersion != null) {
                onVersionChanged(newVersion);
            }
        });

        MinecraftVersion activeVersion = safeGetActiveVersion();
        if (activeVersion != null) {
            versionBox.getSelectionModel().select(activeVersion);
        } else {
            updateStatus("Select a Minecraft version or import item catalogs.");
        }
    }

    @FXML
    private void onAddJar() {
        Window window = addJarButton != null ? addJarButton.getScene().getWindow() : null;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Minecraft JAR");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JAR Files", "*.jar"));
        File file = window != null ? chooser.showOpenDialog(window) : chooser.showOpenDialog(null);
        if (file == null) {
            updateStatus("Jar selection cancelled.");
            return;
        }

        Path jarPath = file.toPath();
        MinecraftVersion targetVersion = Optional.ofNullable(versionBox.getSelectionModel().getSelectedItem())
                .orElseGet(this::safeGetActiveVersion);
        String versionLabel = targetVersion != null ? targetVersion.getId() : "unknown";

        try {
            JarScanner.JarScanResult scan = JarScanner.scanModJar(jarPath, versionLabel);
            var catalog = ItemCatalogExtractor.extract(jarPath, file.getName(), versionLabel, false);
            catalogImportService.importCatalog(catalog);
            rebuildVersionCatalog(targetVersion);
            updateStatus(String.format(Locale.ROOT,
                    "Imported %s with %d entries for %s.",
                    file.getName(),
                    scan.entries().size(),
                    versionLabel));
            LOGGER.log(Level.INFO, "Imported JAR {0} containing {1} entries for version {2}",
                    new Object[]{file.getAbsolutePath(), scan.entries().size(), versionLabel});
        } catch (IOException ex) {
            String message = "Failed to scan JAR: " + ex.getMessage();
            updateStatus(message);
            LOGGER.log(Level.WARNING, message, ex);
        } catch (RuntimeException ex) {
            String message = "Failed to import catalog: " + ex.getMessage();
            updateStatus(message);
            LOGGER.log(Level.WARNING, message, ex);
        }
    }

    @FXML
    private void onClearIconCache() {
        try {
            cacheManager.clearIcons();
            updateStatus("Icon cache cleared.");
            LOGGER.info("Icon cache cleared via settings panel.");
        } catch (RuntimeException ex) {
            String message = "Failed to clear icon cache: " + ex.getMessage();
            updateStatus(message);
            LOGGER.log(Level.WARNING, message, ex);
        }
    }

    private void onVersionChanged(MinecraftVersion newVersion) {
        try {
            versionCatalog.setActiveVersion(newVersion);
            updateStatus("Active Minecraft version set to " + newVersion.getId() + ".");
            LOGGER.log(Level.INFO, "Active Minecraft version changed to {0}", newVersion.getId());
        } catch (RuntimeException ex) {
            String message = "Unable to switch version: " + ex.getMessage();
            updateStatus(message);
            LOGGER.log(Level.WARNING, message, ex);
        }
    }

    private void rebuildVersionCatalog(MinecraftVersion desiredVersion) {
        UiServiceLocator.rebuildVersionCatalog();
        versionCatalog = UiServiceLocator.getVersionCatalog();
        if (desiredVersion != null) {
            try {
                versionCatalog.setActiveVersion(desiredVersion);
            } catch (RuntimeException ex) {
                LOGGER.log(Level.WARNING, "Failed to restore active version after import", ex);
            }
        }
    }

    private MinecraftVersion safeGetActiveVersion() {
        try {
            return versionCatalog.getActiveVersion();
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Unable to read active Minecraft version", ex);
            return null;
        }
    }

    private void updateStatus(String message) {
        if (statusLabel == null) {
            return;
        }
        Platform.runLater(() -> statusLabel.setText(message));
    }
}
