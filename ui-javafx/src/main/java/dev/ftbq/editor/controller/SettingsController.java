package dev.ftbq.editor.controller;

import dev.ftbq.editor.ThemeService;
import dev.ftbq.editor.assets.CacheManager;
import dev.ftbq.editor.domain.version.MinecraftVersion;
import dev.ftbq.editor.domain.version.VersionCatalog;
import dev.ftbq.editor.ingest.ItemCatalogExtractor;
import dev.ftbq.editor.ingest.JarScanner;
import dev.ftbq.editor.services.bus.ServiceLocator;
import dev.ftbq.editor.services.catalog.CatalogImportService;
import dev.ftbq.editor.services.logging.StructuredLogger;
import dev.ftbq.editor.services.mods.ModRegistryService;
import dev.ftbq.editor.services.mods.RegisteredMod;
import dev.ftbq.editor.support.UiServiceLocator;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Window;

/**
 * Controller for the application settings view.
 */
public class SettingsController {

    @FXML
    private ComboBox<MinecraftVersion> versionBox;
    @FXML
    private Button addJarButton;
    @FXML
    private Button clearIconCacheButton;
    @FXML
    private Label statusLabel;
    @FXML
    private CheckBox darkModeCheckBox;
    @FXML
    private CheckBox createZhTemplateCheckBox;
    @FXML
    private Spinner<Integer> autosaveSpinner;
    @FXML
    private Button applyAutosaveButton;

    private VersionCatalog versionCatalog;
    private final CacheManager cacheManager;
    private final CatalogImportService catalogImportService;
    private final ModRegistryService modRegistryService;
    private final StructuredLogger logger;
    private final ThemeService themeService;
    private boolean updatingThemeSelection;
    private boolean updatingZhTemplate;
    private AutosaveSettings autosaveSettings;

    public SettingsController() {
        this(
                UiServiceLocator.getVersionCatalog(),
                UiServiceLocator.getCacheManager(),
                new CatalogImportService(
                        UiServiceLocator.getStoreDao(),
                        ServiceLocator.loggerFactory()),
                UiServiceLocator.getModRegistryService(),
                ServiceLocator.loggerFactory().create(SettingsController.class),
                ThemeService.getInstance());
    }

    SettingsController(VersionCatalog versionCatalog,
                       CacheManager cacheManager,
                       CatalogImportService catalogImportService,
                       ModRegistryService modRegistryService,
                       StructuredLogger logger,
                       ThemeService themeService) {
        this.versionCatalog = Objects.requireNonNull(versionCatalog, "versionCatalog");
        this.cacheManager = Objects.requireNonNull(cacheManager, "cacheManager");
        this.catalogImportService = Objects.requireNonNull(catalogImportService, "catalogImportService");
        this.modRegistryService = Objects.requireNonNull(modRegistryService, "modRegistryService");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.themeService = Objects.requireNonNull(themeService, "themeService");
    }

    @FXML
    public void initialize() {
        if (versionBox != null) {
            versionBox.getItems().setAll(Arrays.asList(MinecraftVersion.values()));
            versionBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVersion, newVersion) -> {
                if (newVersion != null) {
                    onVersionChanged(newVersion);
                }
            });
        }

        if (clearIconCacheButton != null && clearIconCacheButton.getAccessibleText() == null) {
            clearIconCacheButton.setAccessibleText("Clear cached icons");
        }
        if (addJarButton != null && addJarButton.getAccessibleText() == null) {
            addJarButton.setAccessibleText("Import Minecraft JAR");
        }
        if (applyAutosaveButton != null && applyAutosaveButton.getAccessibleText() == null) {
            applyAutosaveButton.setAccessibleText("Apply autosave interval");
        }

        configureAutosaveSpinner();

        if (createZhTemplateCheckBox != null && createZhTemplateCheckBox.getAccessibleText() == null) {
            createZhTemplateCheckBox.setAccessibleText("Toggle zh_cn template generation");
        }
        if (createZhTemplateCheckBox != null) {
            createZhTemplateCheckBox.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                if (updatingZhTemplate || autosaveSettings == null) {
                    return;
                }
                try {
                    autosaveSettings.updateZhTemplateEnabled(isSelected);
                    updateStatus(isSelected
                            ? "zh_cn template will reuse English quest text on export."
                            : "zh_cn template will include empty values on export.");
                } catch (RuntimeException ex) {
                    logger.warn("Failed to update zh_cn template preference", ex);
                    updateStatus("Failed to update zh_cn template preference: " + ex.getMessage());
                    updatingZhTemplate = true;
                    createZhTemplateCheckBox.setSelected(wasSelected);
                    updatingZhTemplate = false;
                }
            });
        }

        if (darkModeCheckBox != null) {
            darkModeCheckBox.setAccessibleText("Toggle dark mode");
            updatingThemeSelection = true;
            darkModeCheckBox.setSelected(themeService.getTheme() == ThemeService.Theme.DARK);
            updatingThemeSelection = false;
            darkModeCheckBox.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                if (updatingThemeSelection) {
                    return;
                }
                themeService.setTheme(isSelected ? ThemeService.Theme.DARK : ThemeService.Theme.LIGHT);
            });
            themeService.themeProperty().addListener((obs, oldTheme, newTheme) -> {
                if (darkModeCheckBox == null) {
                    return;
                }
                boolean dark = newTheme == ThemeService.Theme.DARK;
                if (darkModeCheckBox.isSelected() != dark) {
                    updatingThemeSelection = true;
                    darkModeCheckBox.setSelected(dark);
                    updatingThemeSelection = false;
                }
            });
        }

        MinecraftVersion activeVersion = safeGetActiveVersion();
        if (versionBox != null && activeVersion != null) {
            versionBox.getSelectionModel().select(activeVersion);
        } else if (activeVersion == null) {
            updateStatus("Select a Minecraft version or import item catalogs.");
        }
        refreshAutosaveSpinner();
    }

    public void setAutosaveSettings(AutosaveSettings autosaveSettings) {
        this.autosaveSettings = Objects.requireNonNull(autosaveSettings, "autosaveSettings");
        refreshAutosaveSpinner();
    }

    @FXML
    private void onAddJar() {
        Window window = addJarButton != null && addJarButton.getScene() != null
                ? addJarButton.getScene().getWindow()
                : null;
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Mod JARs");
        FileChooser.ExtensionFilter jarFilter = new FileChooser.ExtensionFilter("Mod JARs", "*.jar");
        chooser.getExtensionFilters().setAll(jarFilter);
        chooser.setSelectedExtensionFilter(jarFilter);
        File initialDir = resolveInitialDirectory();
        if (initialDir != null) {
            chooser.setInitialDirectory(initialDir);
        }
        List<File> selectedFiles = window != null
                ? chooser.showOpenMultipleDialog(window)
                : chooser.showOpenMultipleDialog(null);
        if (selectedFiles == null || selectedFiles.isEmpty()) {
            updateStatus("Jar selection cancelled.");
            logger.debug("Jar selection cancelled by user");
            return;
        }

        MinecraftVersion targetVersion = Optional.ofNullable(versionBox.getSelectionModel().getSelectedItem())
                .orElseGet(this::safeGetActiveVersion);
        String versionLabel = targetVersion != null ? targetVersion.getId() : "unknown";

        for (File file : selectedFiles) {
            if (file == null) {
                continue;
            }
            if (!file.exists()) {
                logger.warn("Jar file missing during import",
                        StructuredLogger.field("jar", file.getAbsolutePath()));
                updateStatus("Jar file missing: " + file.getName());
                continue;
            }

            Path jarPath = file.toPath();
            try {
                JarScanner.JarScanResult scan = JarScanner.scanModJar(jarPath, versionLabel);
                var catalog = ItemCatalogExtractor.extract(jarPath, file.getName(), versionLabel, false);
                catalogImportService.importCatalog(catalog);
                List<RegisteredMod> registeredMods = modRegistryService.registerMod(catalog);
                rebuildVersionCatalog(targetVersion);
                int entryCount = scan.entries().size();
                updateStatus(String.format(Locale.ROOT,
                        "Imported %s with %d entries for %s.",
                        file.getName(),
                        entryCount,
                        versionLabel));
                logger.info("Mod JAR imported",
                        StructuredLogger.field("jar", file.getAbsolutePath()),
                        StructuredLogger.field("entries", entryCount),
                        StructuredLogger.field("version", versionLabel),
                        StructuredLogger.field("registeredMods", registeredMods.size()));
            } catch (IOException ex) {
                String message = "Failed to scan JAR: " + ex.getMessage();
                updateStatus(message);
                logger.warn("Jar scan failed", ex,
                        StructuredLogger.field("jar", file.getAbsolutePath()),
                        StructuredLogger.field("version", versionLabel));
            } catch (RuntimeException ex) {
                String message = "Failed to import catalog: " + ex.getMessage();
                updateStatus(message);
                logger.warn("Catalog import failed", ex,
                        StructuredLogger.field("jar", file.getAbsolutePath()),
                        StructuredLogger.field("version", versionLabel));
            }
        }
    }

    @FXML
    private void onClearIconCache() {
        try {
            cacheManager.clearIcons();
            updateStatus("Icon cache cleared.");
            logger.info("Icon cache cleared via settings panel");
        } catch (RuntimeException ex) {
            String message = "Failed to clear icon cache: " + ex.getMessage();
            updateStatus(message);
            logger.warn("Icon cache clear failed", ex);
        }
    }

    private File resolveInitialDirectory() {
        String configured = System.getProperty("ftbq.modJarDir");
        if (configured != null && !configured.isBlank()) {
            File dir = new File(configured);
            if (dir.exists() && dir.isDirectory()) {
                return dir;
            }
        }
        String userDir = System.getProperty("user.dir");
        if (userDir != null && !userDir.isBlank()) {
            File dir = new File(userDir);
            if (dir.exists() && dir.isDirectory()) {
                return dir;
            }
        }
        return null;
    }

    private void onVersionChanged(MinecraftVersion newVersion) {
        try {
            versionCatalog.setActiveVersion(newVersion);
            updateStatus("Active Minecraft version set to " + newVersion.getId() + ".");
            logger.info("Active version changed", StructuredLogger.field("version", newVersion.getId()));
        } catch (RuntimeException ex) {
            String message = "Unable to switch version: " + ex.getMessage();
            updateStatus(message);
            logger.warn("Unable to switch active version", ex,
                    StructuredLogger.field("version", newVersion.getId()));
        }
    }

    private void rebuildVersionCatalog(MinecraftVersion desiredVersion) {
        UiServiceLocator.rebuildVersionCatalog();
        versionCatalog = UiServiceLocator.getVersionCatalog();
        if (desiredVersion != null && versionBox != null) {
            try {
                versionCatalog.setActiveVersion(desiredVersion);
                versionBox.getSelectionModel().select(desiredVersion);
            } catch (RuntimeException ex) {
                logger.warn("Failed to restore active version after import", ex,
                        StructuredLogger.field("version", desiredVersion.getId()));
            }
        }
    }

    private MinecraftVersion safeGetActiveVersion() {
        try {
            return versionCatalog.getActiveVersion();
        } catch (RuntimeException ex) {
            logger.warn("Unable to read active Minecraft version", ex);
            return null;
        }
    }

    private void updateStatus(String message) {
        if (statusLabel == null) {
            return;
        }
        Platform.runLater(() -> statusLabel.setText(message));
    }

    @FXML
    private void onApplyAutosave() {
        if (autosaveSpinner == null) {
            return;
        }
        if (autosaveSettings == null) {
            updateStatus("Autosave is not available in this context.");
            return;
        }
        Integer value = autosaveSpinner.getValue();
        int minutes = value == null ? 0 : Math.max(0, value);
        try {
            autosaveSettings.updateIntervalMinutes(minutes);
            if (minutes == 0) {
                updateStatus("Autosave disabled.");
            } else {
                updateStatus("Autosave interval set to " + minutes + " minute" + (minutes == 1 ? "" : "s") + ".");
            }
        } catch (RuntimeException ex) {
            updateStatus("Failed to update autosave interval: " + ex.getMessage());
            logger.warn("Autosave interval update failed", ex);
        }
        refreshAutosaveSpinner();
    }

    private void configureAutosaveSpinner() {
        if (autosaveSpinner == null) {
            return;
        }
        SpinnerValueFactory.IntegerSpinnerValueFactory factory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 120, 1);
        autosaveSpinner.setValueFactory(factory);
        autosaveSpinner.setEditable(true);
        autosaveSpinner.focusedProperty().addListener((obs, oldFocused, newFocused) -> {
            if (!newFocused) {
                commitAutosaveEditorValue();
            }
        });
    }

    private void commitAutosaveEditorValue() {
        if (autosaveSpinner == null || autosaveSpinner.getValueFactory() == null) {
            return;
        }
        try {
            autosaveSpinner.increment(0);
        } catch (RuntimeException ignored) {
            autosaveSpinner.getValueFactory().setValue(0);
        }
    }

    private void refreshAutosaveSpinner() {
        if (autosaveSpinner == null || autosaveSpinner.getValueFactory() == null) {
            return;
        }
        int minutes = 0;
        boolean zhTemplate = false;
        if (autosaveSettings != null) {
            try {
                minutes = Math.max(0, autosaveSettings.getIntervalMinutes());
            } catch (RuntimeException ex) {
                logger.warn("Failed to read autosave interval", ex);
            }
            try {
                zhTemplate = autosaveSettings.isZhTemplateEnabled();
            } catch (RuntimeException ex) {
                logger.warn("Failed to read zh_cn template preference", ex);
            }
        }
        final int applied = minutes;
        final boolean zhEnabled = zhTemplate;
        Platform.runLater(() -> {
            autosaveSpinner.getValueFactory().setValue(applied);
            if (createZhTemplateCheckBox != null) {
                updatingZhTemplate = true;
                createZhTemplateCheckBox.setSelected(zhEnabled);
                updatingZhTemplate = false;
            }
        });
    }

    public interface AutosaveSettings {
        int getIntervalMinutes();

        void updateIntervalMinutes(int minutes);

        boolean isZhTemplateEnabled();

        void updateZhTemplateEnabled(boolean enabled);
    }
}


