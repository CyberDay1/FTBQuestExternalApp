package dev.ftbq.editor.ui;

import dev.ftbq.editor.ThemeService;
import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.io.snbt.AiQuestBridge;
import dev.ftbq.editor.ingest.JarScanner;
import dev.ftbq.editor.services.ai.QuestGenerationService;
import dev.ftbq.editor.services.generator.ModIntent;
import dev.ftbq.editor.services.generator.QuestDesignSpec;
import dev.ftbq.editor.services.generator.QuestLimits;
import dev.ftbq.editor.services.generator.RewardConfiguration;
import dev.ftbq.editor.services.mods.ModRegistryService;
import dev.ftbq.editor.services.mods.RegisteredMod;
import dev.ftbq.editor.support.UiServiceLocator;
import dev.ftbq.editor.ui.model.ModSelectionModel;
import dev.ftbq.editor.ui.model.RewardSelectionModel;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tab that surfaces the AI chapter generation workflow with full dark theme styling.
 */
public final class AiQuestCreationTab extends Tab {

    private static final int DEFAULT_QUEST_COUNT = 8;
    private static final String DEFAULT_PROMPT_PREFIX = "User prompt:";
    private static final Logger LOGGER = LoggerFactory.getLogger(AiQuestCreationTab.class);

    private final QuestGenerationService questGenerationService;
    private final AiQuestBridge aiQuestBridge;
    private final ModRegistryService modRegistryService;
    private final ModSelectionModel modSelectionModel;
    private final RewardSelectionModel rewardSelectionModel;
    private final Consumer<List<RegisteredMod>> registryListener;
    private final Map<ModSelectionModel.ModOption, ChangeListener<Boolean>> optionListeners = new IdentityHashMap<>();
    private final Map<String, Integer> proxyItemCounts = new HashMap<>();

    private Supplier<QuestDesignSpec> designSpecSupplier;
    private Supplier<ModIntent> modIntentSupplier;
    private Supplier<QuestFile> questFileSupplier;
    private Path workspaceRoot;

    private MenuButton modSelectionMenu;
    private TextArea promptArea;
    private Spinner<Integer> questCountSpinner;
    private ComboBox<RewardMode> rewardModeCombo;
    private MenuButton lootTableMenu;
    private Label rewardSummaryLabel;
    private Label modWarningLabel;
    private TextArea snbtPreview;
    private Button saveDraftButton;

    public AiQuestCreationTab() {
        this(new QuestGenerationService(),
                new AiQuestBridge(),
                UiServiceLocator.getModRegistryService(),
                new ModSelectionModel(),
                new RewardSelectionModel());
    }

    AiQuestCreationTab(QuestGenerationService questGenerationService,
                       AiQuestBridge aiQuestBridge,
                       ModRegistryService modRegistryService,
                       ModSelectionModel modSelectionModel,
                       RewardSelectionModel rewardSelectionModel) {
        super("AI Quest Creation");
        this.questGenerationService = Objects.requireNonNull(questGenerationService, "questGenerationService");
        this.aiQuestBridge = Objects.requireNonNull(aiQuestBridge, "aiQuestBridge");
        this.modRegistryService = Objects.requireNonNull(modRegistryService, "modRegistryService");
        this.modSelectionModel = Objects.requireNonNull(modSelectionModel, "modSelectionModel");
        this.rewardSelectionModel = Objects.requireNonNull(rewardSelectionModel, "rewardSelectionModel");
        this.registryListener = mods -> Platform.runLater(() -> this.modSelectionModel.setAvailableMods(mods));
        this.modRegistryService.addListener(registryListener);
        this.modSelectionModel.setAvailableMods(this.modRegistryService.listMods());

        setClosable(false);
        getStyleClass().add("dark-theme");
        setContent(buildContent());
        initialiseBindings();
    }

    private Node buildContent() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("dark-theme");
        root.setPadding(new Insets(16));

        SplitPane splitPane = new SplitPane();
        splitPane.getItems().addAll(buildFormPane(), buildPreviewPane());
        splitPane.setDividerPositions(0.42);
        root.setCenter(splitPane);
        return root;
    }

    private Node buildFormPane() {
        VBox form = new VBox(16);
        form.setFillWidth(true);
        form.setPadding(new Insets(12));

        Label heading = new Label("Configure generation");
        form.getChildren().add(heading);

        Label modsLabel = new Label("Mods");
        form.getChildren().add(modsLabel);

        modSelectionMenu = new MenuButton("Select mods");
        modSelectionMenu.getStyleClass().add("combo-box");
        modSelectionMenu.setAccessibleText("Select mods for AI generation context");
        modSelectionMenu.setMaxWidth(Double.MAX_VALUE);
        modSelectionMenu.textProperty().bind(modSelectionModel.summaryProperty());
        VBox.setVgrow(modSelectionMenu, Priority.NEVER);

        modWarningLabel = new Label();
        modWarningLabel.setManaged(false);
        modWarningLabel.setVisible(false);
        modWarningLabel.setStyle("-fx-text-fill: -accent;");

        form.getChildren().addAll(modSelectionMenu, modWarningLabel);

        Label promptLabel = new Label("Design prompt");
        promptArea = new TextArea();
        promptArea.setPromptText("Describe the chapter tone, milestones, or mod interactions you want.");
        promptArea.setWrapText(true);
        promptArea.setPrefRowCount(6);
        promptArea.setStyle("-fx-control-inner-background: -bg-elev; -fx-font-family: 'Segoe UI', 'Sans-Serif';");
        form.getChildren().addAll(promptLabel, promptArea);

        Label questCountLabel = new Label("Quest count");
        questCountSpinner = new Spinner<>();
        questCountSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, QuestLimits.MAX_AI_QUESTS, DEFAULT_QUEST_COUNT));
        questCountSpinner.setEditable(true);
        questCountSpinner.setMaxWidth(160);
        questCountSpinner.setTooltip(new Tooltip("Select how many quests to generate (1–" + QuestLimits.MAX_AI_QUESTS + ")"));
        form.getChildren().addAll(questCountLabel, questCountSpinner);

        Label rewardHeading = new Label("Rewards");
        rewardModeCombo = new ComboBox<>();
        rewardModeCombo.getItems().setAll(RewardMode.values());
        rewardModeCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(RewardMode mode) {
                return mode == null ? "" : mode.displayName;
            }

            @Override
            public RewardMode fromString(String string) {
                return rewardModeCombo.getItems().stream()
                        .filter(mode -> Objects.equals(mode.displayName, string))
                        .findFirst()
                        .orElse(null);
            }
        });
        rewardModeCombo.setValue(RewardMode.ALL_TYPES);
        rewardModeCombo.setMaxWidth(Double.MAX_VALUE);

        lootTableMenu = new MenuButton("Loot tables");
        lootTableMenu.getStyleClass().add("combo-box");
        lootTableMenu.setMaxWidth(Double.MAX_VALUE);
        lootTableMenu.setDisable(true);
        lootTableMenu.setAccessibleText("Select existing loot tables for AI rewards");

        rewardSummaryLabel = new Label();
        rewardSummaryLabel.textProperty().bind(rewardSelectionModel.summaryProperty());

        form.getChildren().addAll(rewardHeading, rewardModeCombo, lootTableMenu, rewardSummaryLabel);

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        form.getChildren().add(spacer);

        Button generateButton = new Button("Generate chapter");
        generateButton.getStyleClass().add("accent-button");
        generateButton.setMaxWidth(Double.MAX_VALUE);
        generateButton.setOnAction(event -> onGenerate());

        saveDraftButton = new Button("Save SNBT draft");
        saveDraftButton.setDisable(true);
        saveDraftButton.setMaxWidth(Double.MAX_VALUE);
        saveDraftButton.setOnAction(event -> onSaveDraft());

        VBox buttonBox = new VBox(8, generateButton, saveDraftButton);
        form.getChildren().add(buttonBox);

        return form;
    }

    private Node buildPreviewPane() {
        VBox previewWrapper = new VBox(12);
        previewWrapper.setPadding(new Insets(12));
        previewWrapper.setFillWidth(true);

        Label previewHeading = new Label("SNBT preview");

        snbtPreview = new TextArea();
        snbtPreview.setEditable(false);
        snbtPreview.setWrapText(false);
        snbtPreview.setStyle("-fx-font-family: 'JetBrains Mono', 'Consolas', 'Courier New', monospace; -fx-font-size: 12px; -fx-control-inner-background: -bg-elev; -fx-text-fill: -fg;");
        snbtPreview.setPromptText("Generated SNBT will appear here.");
        VBox.setVgrow(snbtPreview, Priority.ALWAYS);

        previewWrapper.getChildren().addAll(previewHeading, new Separator(), snbtPreview);
        return previewWrapper;
    }

    private void initialiseBindings() {
        modSelectionModel.options().addListener((ListChangeListener<ModSelectionModel.ModOption>) change -> refreshModSelectionMenu());
        modSelectionModel.warningMessageProperty().addListener((obs, oldValue, newValue) -> showModWarning(newValue));
        rewardSelectionModel.availableLootTables().addListener((ListChangeListener<String>) change -> refreshLootTableMenu());
        rewardSelectionModel.selectedLootTables().addListener((ListChangeListener<String>) change -> refreshLootTableMenuState());
        rewardModeCombo.valueProperty().addListener((obs, oldValue, newValue) -> applyRewardMode(newValue));
        rewardSelectionModel.summaryProperty().addListener((obs, oldValue, newValue) -> updateLootMenuState());
        applyRewardMode(rewardModeCombo.getValue());
        refreshModSelectionMenu();
        refreshLootTableMenu();
    }

    public void setDesignSpecSupplier(Supplier<QuestDesignSpec> designSpecSupplier) {
        this.designSpecSupplier = designSpecSupplier;
    }

    public void setModIntentSupplier(Supplier<ModIntent> modIntentSupplier) {
        this.modIntentSupplier = modIntentSupplier;
    }

    public void setQuestFileSupplier(Supplier<QuestFile> questFileSupplier) {
        this.questFileSupplier = questFileSupplier;
        updateRewardLootTables();
    }

    public void setWorkspaceRoot(Path workspaceRoot) {
        this.workspaceRoot = workspaceRoot;
    }

    private void onGenerate() {
        QuestDesignSpec baseSpec = resolveDesignSpec();
        ModIntent baseIntent = resolveModIntent();
        if (baseSpec == null || baseIntent == null) {
            showError("Generation unavailable", "Design specification or mod intent is not configured.");
            return;
        }

        questCountSpinner.increment(0);
        int questCount = Optional.ofNullable(questCountSpinner.getValue()).orElse(DEFAULT_QUEST_COUNT);
        if (questCount > QuestLimits.MAX_AI_QUESTS) {
            showWarning("Quest limit exceeded",
                    "AI generation supports up to " + QuestLimits.MAX_AI_QUESTS + " quests per chapter. Adjust the quest count and retry.");
            return;
        }

        QuestDesignSpec designSpec = new QuestDesignSpec(
                baseSpec.theme(),
                baseSpec.difficultyCurve(),
                baseSpec.gatingRules(),
                baseSpec.progressionAxes(),
                baseSpec.itemBlacklist(),
                baseSpec.allowedTasks(),
                questCount,
                baseSpec.rewardBudget()
        );

        ModIntent modIntent = appendPrompt(baseIntent,
                promptArea != null ? promptArea.getText() : "",
                modSelectionModel.selectedModsSnapshot(),
                rewardSelectionModel.toConfiguration());

        if (saveDraftButton != null) {
            saveDraftButton.setDisable(true);
        }

        try {
            String snbt = questGenerationService.generateChapterDraft(designSpec, modIntent);
            aiQuestBridge.parse(snbt);
            aiQuestBridge.lastFormattedText().ifPresentOrElse(
                    formatted -> snbtPreview.setText(formatted),
                    () -> snbtPreview.setText(snbt)
            );
            if (saveDraftButton != null) {
                saveDraftButton.setDisable(false);
            }
        } catch (IllegalStateException illegalStateException) {
            showError("Generation failed", illegalStateException.getMessage());
        } catch (Exception ex) {
            showError("AI generation failed", ex.getMessage());
        } finally {
            if (saveDraftButton != null && aiQuestBridge.lastFormattedText().isPresent()) {
                saveDraftButton.setDisable(false);
            }
        }
    }

    private void onSaveDraft() {
        Path root = workspaceRoot;
        if (root == null) {
            root = defaultWorkspace();
        }

        try {
            Path saved = aiQuestBridge.saveDraft(root);
            showInfo("Draft saved", "Draft written to " + saved.toAbsolutePath());
        } catch (Exception ex) {
            showError("Failed to save draft", ex.getMessage());
        }
    }

    private void refreshModSelectionMenu() {
        if (modSelectionMenu == null) {
            return;
        }
        optionListeners.forEach((option, listener) -> option.selectedProperty().removeListener(listener));
        optionListeners.clear();
        modSelectionMenu.getItems().clear();

        Set<String> activeKeys = modSelectionModel.options().stream()
                .map(option -> proxyCacheKey(option.mod()))
                .collect(Collectors.toSet());
        proxyItemCounts.keySet().retainAll(activeKeys);

        for (ModSelectionModel.ModOption option : modSelectionModel.options()) {
            RegisteredMod mod = option.mod();
            int itemCount = resolveItemCount(mod);
            CheckMenuItem item = new CheckMenuItem(buildModLabel(mod, itemCount));
            item.setSelected(option.isSelected());
            item.setOnAction(event -> {
                boolean success = modSelectionModel.setOptionSelected(option, !option.isSelected());
                if (!success) {
                    item.setSelected(option.isSelected());
                }
            });
            ChangeListener<Boolean> listener = (obs, oldValue, newValue) -> {
                if (item.isSelected() != newValue) {
                    item.setSelected(newValue);
                }
            };
            option.selectedProperty().addListener(listener);
            optionListeners.put(option, listener);
            modSelectionMenu.getItems().add(item);
        }

        modSelectionMenu.setDisable(modSelectionModel.options().isEmpty());
    }

    private void refreshLootTableMenu() {
        if (lootTableMenu == null) {
            return;
        }
        lootTableMenu.getItems().clear();

        boolean lootEnabled = rewardSelectionModel.lootTableRewardsEnabledProperty().get();
        for (String tableId : rewardSelectionModel.availableLootTables()) {
            CheckMenuItem lootItem = new CheckMenuItem(tableId);
            lootItem.setDisable(!lootEnabled);
            lootItem.setSelected(rewardSelectionModel.selectedLootTables().contains(tableId));
            lootItem.setOnAction(event -> {
                rewardSelectionModel.toggleLootTable(tableId);
                lootItem.setSelected(rewardSelectionModel.selectedLootTables().contains(tableId));
            });
            lootTableMenu.getItems().add(lootItem);
        }
        updateLootMenuState();
    }

    private void refreshLootTableMenuState() {
        if (lootTableMenu == null) {
            return;
        }
        for (var menuItem : lootTableMenu.getItems()) {
            if (menuItem instanceof CheckMenuItem item) {
                item.setDisable(!rewardSelectionModel.lootTableRewardsEnabledProperty().get());
                item.setSelected(rewardSelectionModel.selectedLootTables().contains(item.getText()));
            }
        }
        updateLootMenuState();
    }

    private void applyRewardMode(RewardMode rewardMode) {
        if (rewardMode == null) {
            return;
        }
        rewardSelectionModel.itemRewardsEnabledProperty().set(rewardMode.allowItemRewards);
        rewardSelectionModel.xpRewardsEnabledProperty().set(rewardMode.allowXpRewards);
        rewardSelectionModel.lootTableRewardsEnabledProperty().set(rewardMode.allowLootRewards);
        refreshLootTableMenu();
    }

    private void updateLootMenuState() {
        boolean lootEnabled = rewardSelectionModel.lootTableRewardsEnabledProperty().get();
        lootTableMenu.setDisable(!lootEnabled || rewardSelectionModel.availableLootTables().isEmpty());
    }

    private void showModWarning(String warning) {
        boolean hasWarning = warning != null && !warning.isBlank();
        modWarningLabel.setText(hasWarning ? warning : "");
        modWarningLabel.setManaged(hasWarning);
        modWarningLabel.setVisible(hasWarning);
    }

    private QuestDesignSpec resolveDesignSpec() {
        if (designSpecSupplier != null) {
            return designSpecSupplier.get();
        }
        return defaultDesignSpec();
    }

    private ModIntent resolveModIntent() {
        if (modIntentSupplier != null) {
            return modIntentSupplier.get();
        }
        return defaultModIntent();
    }

    private QuestDesignSpec defaultDesignSpec() {
        return new QuestDesignSpec(
                "getting started",
                List.of("Intro", "Progress", "Challenge"),
                List.of("Unlock basic tools", "Introduce automation"),
                List.of("Exploration", "Technology"),
                Set.of(),
                List.of("collect", "craft", "kill"),
                DEFAULT_QUEST_COUNT,
                200
        );
    }

    private ModIntent defaultModIntent() {
        return new ModIntent(
                "minecraft",
                List.of("Core gameplay"),
                "Base prompt",
                List.of()
        );
    }

    private ModIntent appendPrompt(ModIntent baseIntent,
                                   String prompt,
                                   List<RegisteredMod> selectedMods,
                                   RewardConfiguration rewardConfiguration) {
        StringBuilder notes = new StringBuilder(Optional.ofNullable(baseIntent.progressionNotes()).orElse(""));

        if (selectedMods != null && !selectedMods.isEmpty()) {
            if (notes.length() > 0) {
                notes.append(System.lineSeparator());
            }
            String modList = selectedMods.stream()
                    .map(RegisteredMod::displayName)
                    .collect(Collectors.joining(", "));
            notes.append("Focus integration on: ").append(modList);
        }

        if (rewardConfiguration != null && rewardConfiguration.hasAnyRewardTypeEnabled()) {
            if (notes.length() > 0) {
                notes.append(System.lineSeparator());
            }
            notes.append("Reward preferences → ");
            notes.append(rewardConfiguration.allowItemRewards() ? "items✔" : "items✖");
            notes.append(" · ");
            notes.append(rewardConfiguration.allowXpRewards() ? "xp✔" : "xp✖");
            notes.append(" · ");
            notes.append(rewardConfiguration.allowLootTableRewards() ? "loot✔" : "loot✖");
            if (rewardConfiguration.allowLootTableRewards() && !rewardConfiguration.preferredLootTables().isEmpty()) {
                notes.append(" [");
                notes.append(String.join(", ", rewardConfiguration.preferredLootTables()));
                notes.append(']');
            }
        }

        if (prompt != null && !prompt.isBlank()) {
            if (notes.length() > 0) {
                notes.append(System.lineSeparator());
            }
            notes.append(DEFAULT_PROMPT_PREFIX).append(' ').append(prompt.trim());
        }

        return new ModIntent(
                baseIntent.modId(),
                baseIntent.features(),
                notes.toString(),
                baseIntent.exampleReferences()
        );
    }

    private String buildModLabel(RegisteredMod mod) {
        return buildModLabel(mod, resolveItemCount(mod));
    }

    private String buildModLabel(RegisteredMod mod, int itemCount) {
        StringBuilder builder = new StringBuilder(mod.displayName())
                .append(" [")
                .append(mod.modId())
                .append(']');
        if (mod.version() != null && !mod.version().isBlank()) {
            builder.append(" v").append(mod.version());
        }
        builder.append(" • ")
                .append(itemCount)
                .append(itemCount == 1 ? " item" : " items");
        return builder.toString();
    }

    private int resolveItemCount(RegisteredMod mod) {
        if (mod == null) {
            return 0;
        }
        int actual = mod.itemCount();
        if (actual > 0) {
            return actual;
        }
        String key = proxyCacheKey(mod);
        return proxyItemCounts.computeIfAbsent(key, ignored -> loadProxyItemCount(mod));
    }

    private int loadProxyItemCount(RegisteredMod mod) {
        String sourceJar = mod.sourceJar();
        if (sourceJar == null || sourceJar.isBlank()) {
            return 0;
        }
        try {
            var proxyItems = JarScanner.extractProxyItems(Paths.get(sourceJar), mod.version());
            return proxyItems.size();
        } catch (Exception ex) {
            LOGGER.warn("Failed to resolve proxy items for mod {} from {}", mod.modId(), sourceJar, ex);
            return 0;
        }
    }

    private String proxyCacheKey(RegisteredMod mod) {
        String source = mod.sourceJar() == null ? "" : mod.sourceJar();
        return mod.modId() + "@" + source;
    }

    private void updateRewardLootTables() {
        QuestFile questFile = questFileSupplier != null ? questFileSupplier.get() : null;
        List<LootTable> lootTables = questFile != null ? questFile.lootTables() : List.of();
        rewardSelectionModel.setAvailableLootTables(lootTables);
        refreshLootTableMenu();
    }

    private void showError(String title, String message) {
        showAlert(Alert.AlertType.ERROR, title, message);
    }

    private void showInfo(String title, String message) {
        showAlert(Alert.AlertType.INFORMATION, title, message);
    }

    private void showWarning(String title, String message) {
        showAlert(Alert.AlertType.WARNING, title, message);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type, message, ButtonType.OK);
            alert.setTitle(title);
            alert.setHeaderText(null);
            Window owner = findWindow();
            if (owner != null) {
                alert.initOwner(owner);
            }
            alert.setOnShown(event -> {
                Window window = alert.getDialogPane().getScene().getWindow();
                if (window instanceof javafx.stage.Stage stage) {
                    ThemeService.getInstance().registerStage(stage);
                }
            });
            alert.showAndWait();
        });
    }

    private Window findWindow() {
        Node content = getContent();
        if (content != null && content.getScene() != null) {
            return content.getScene().getWindow();
        }
        return null;
    }

    private Path defaultWorkspace() {
        return Paths.get(System.getProperty("ftbq.editor.workspace", "."))
                .toAbsolutePath()
                .normalize();
    }

    private enum RewardMode {
        ALL_TYPES("Items, XP, and loot", true, true, true),
        ITEMS_AND_XP("Items and XP", true, true, false),
        ITEMS_ONLY("Items only", true, false, false),
        XP_ONLY("XP only", false, true, false),
        LOOT_ONLY("Loot tables only", false, false, true);

        private final String displayName;
        private final boolean allowItemRewards;
        private final boolean allowXpRewards;
        private final boolean allowLootRewards;

        RewardMode(String displayName, boolean allowItemRewards, boolean allowXpRewards, boolean allowLootRewards) {
            this.displayName = displayName;
            this.allowItemRewards = allowItemRewards;
            this.allowXpRewards = allowXpRewards;
            this.allowLootRewards = allowLootRewards;
        }
    }
}
