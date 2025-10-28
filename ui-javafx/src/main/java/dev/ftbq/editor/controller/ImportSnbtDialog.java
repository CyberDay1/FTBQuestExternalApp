package dev.ftbq.editor.controller;

import dev.ftbq.editor.domain.ChapterGroup;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.importer.snbt.model.ImportConflictPolicy;
import dev.ftbq.editor.importer.snbt.model.ImportOptions;
import dev.ftbq.editor.importer.snbt.model.ImportedQuestPack;
import dev.ftbq.editor.importer.snbt.model.QuestImportResult;
import dev.ftbq.editor.importer.snbt.model.QuestImportSummary;
import dev.ftbq.editor.services.io.SnbtImportExportService;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

/**
 * Dialog for configuring and executing SNBT quest imports.
 */
public class ImportSnbtDialog extends Dialog<QuestImportResult> {

    private final SnbtImportExportService importService;
    private final QuestFile currentQuestFile;
    private final Path workspace;

    private final TextField pathField = new TextField();
    private final Button browseButton = new Button("Browse...");
    private final ComboBox<GroupOption> groupCombo = new ComboBox<>();
    private final ComboBox<ImportConflictPolicy> chapterPolicyCombo = new ComboBox<>();
    private final ComboBox<ImportConflictPolicy> questPolicyCombo = new ComboBox<>();
    private final CheckBox copyAssetsCheck = new CheckBox("Copy referenced assets");
    private final TreeView<String> previewTree = new TreeView<>();
    private final ListView<String> warningList = new ListView<>();
    private final Label packInfoLabel = new Label("No pack selected");
    private final Label summaryLabel = new Label();
    private final Button importButton;

    private File selectedDirectory;
    private ImportedQuestPack previewPack;
    private QuestImportSummary previewSummary;
    private QuestImportResult importResult;

    public ImportSnbtDialog(Stage owner,
                            SnbtImportExportService importService,
                            QuestFile currentQuestFile,
                            Path workspace) {
        this.importService = Objects.requireNonNull(importService, "importService");
        this.currentQuestFile = Objects.requireNonNull(currentQuestFile, "currentQuestFile");
        this.workspace = Objects.requireNonNull(workspace, "workspace");
        initOwner(owner);
        setTitle("Import Quests from SNBT");
        setHeaderText("Merge quests from an external pack into the current project.");

        ButtonType importButtonType = new ButtonType("Import", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(importButtonType, ButtonType.CANCEL);
        importButton = (Button) getDialogPane().lookupButton(importButtonType);
        importButton.setDisable(true);

        buildContent();
        populateCombos();
        configureListeners();

        importButton.addEventFilter(ActionEvent.ACTION, this::onImport);
        setResultConverter(buttonType -> buttonType == importButtonType ? importResult : null);
    }

    private void buildContent() {
        pathField.setEditable(false);
        pathField.setPrefColumnCount(40);
        HBox pathBox = new HBox(8, pathField, browseButton);
        HBox.setHgrow(pathField, Priority.ALWAYS);

        GridPane form = new GridPane();
        form.setHgap(8);
        form.setVgap(8);
        form.setPadding(new Insets(10));
        form.addRow(0, new Label("Source folder:"), pathBox);
        form.addRow(1, new Label("Target chapter group:"), groupCombo);
        form.addRow(2, new Label("Chapter conflicts:"), chapterPolicyCombo);
        form.addRow(3, new Label("Quest conflicts:"), questPolicyCombo);
        form.add(copyAssetsCheck, 1, 4);

        previewTree.setShowRoot(false);
        previewTree.setRoot(new TreeItem<>("No preview available"));
        previewTree.setPrefHeight(260);

        warningList.setPlaceholder(new Label("No warnings"));
        warningList.setPrefHeight(140);

        BorderPane previewPane = new BorderPane();
        BorderPane.setMargin(packInfoLabel, new Insets(0, 0, 8, 0));
        previewPane.setTop(packInfoLabel);
        previewPane.setCenter(previewTree);
        BorderPane.setMargin(summaryLabel, new Insets(8, 0, 0, 0));
        previewPane.setBottom(summaryLabel);

        BorderPane warningsPane = new BorderPane();
        Label warningsTitle = new Label("Warnings");
        warningsPane.setTop(warningsTitle);
        BorderPane.setMargin(warningsTitle, new Insets(0, 0, 8, 0));
        warningsPane.setCenter(warningList);

        SplitPane splitPane = new SplitPane(previewPane, warningsPane);
        splitPane.setDividerPositions(0.6);

        BorderPane content = new BorderPane();
        content.setTop(form);
        content.setCenter(splitPane);
        getDialogPane().setContent(content);
    }

    private void populateCombos() {
        List<GroupOption> options = new ArrayList<>();
        options.add(new GroupOption(null, "Keep imported groups"));
        for (ChapterGroup group : currentQuestFile.chapterGroups()) {
            String display = group.title() + " (" + group.id() + ")";
            options.add(new GroupOption(group.id(), display));
        }
        groupCombo.setItems(FXCollections.observableArrayList(options));
        groupCombo.getSelectionModel().selectFirst();

        chapterPolicyCombo.setItems(FXCollections.observableArrayList(ImportConflictPolicy.values()));
        questPolicyCombo.setItems(FXCollections.observableArrayList(ImportConflictPolicy.values()));
        chapterPolicyCombo.getSelectionModel().select(ImportConflictPolicy.NEW_IDS);
        questPolicyCombo.getSelectionModel().select(ImportConflictPolicy.NEW_IDS);
        copyAssetsCheck.setSelected(true);
    }

    private void configureListeners() {
        browseButton.setOnAction(event -> openDirectoryChooser());
        chapterPolicyCombo.valueProperty().addListener((obs, old, val) -> refreshPreview());
        questPolicyCombo.valueProperty().addListener((obs, old, val) -> refreshPreview());
        groupCombo.valueProperty().addListener((obs, old, val) -> refreshPreview());
        copyAssetsCheck.selectedProperty().addListener((obs, old, val) -> refreshPreview());
    }

    private void openDirectoryChooser() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select quest pack folder");
        if (selectedDirectory != null && selectedDirectory.isDirectory()) {
            chooser.setInitialDirectory(selectedDirectory);
        }
        File chosen = chooser.showDialog(getOwner());
        if (chosen != null) {
            selectedDirectory = chosen;
            pathField.setText(chosen.getAbsolutePath());
            refreshPreview();
        }
    }

    private void refreshPreview() {
        if (selectedDirectory == null) {
            previewTree.setRoot(new TreeItem<>("No pack selected"));
            previewTree.setShowRoot(true);
            warningList.setItems(FXCollections.observableArrayList());
            packInfoLabel.setText("No pack selected");
            summaryLabel.setText("");
            importButton.setDisable(true);
            return;
        }
        try {
            previewPack = importService.previewPack(selectedDirectory);
            QuestImportResult result = importService.importPack(selectedDirectory, currentQuestFile, createOptions(false));
            previewSummary = result.summary();
            updatePreviewTree(previewSummary);
            updateWarnings(previewSummary);
            packInfoLabel.setText("Pack " + previewPack.title() + " (schema " + previewPack.schemaVersion() + ")");
            summaryLabel.setText(buildSummaryLine(previewSummary));
            importButton.setDisable(false);
        } catch (Exception ex) {
            showError("Failed to preview SNBT", ex.getMessage());
            importButton.setDisable(true);
        }
    }

    private void updatePreviewTree(QuestImportSummary summary) {
        TreeItem<String> root = new TreeItem<>("Preview");
        root.setExpanded(true);
        addCategory(root, "Added Chapters", summary.addedChapters(), "+ ");
        addCategory(root, "Merged Chapters", summary.mergedChapters(), "~ ");
        addCategory(root, "Added Quests", summary.addedQuests(), "+ ");
        addCategory(root, "Renamed IDs", summary.renamedIds(), "→ ");
        previewTree.setRoot(root);
        previewTree.setShowRoot(false);
    }

    private void addCategory(TreeItem<String> root, String title, List<String> entries, String prefix) {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        TreeItem<String> item = new TreeItem<>(title + " (" + entries.size() + ")");
        item.setExpanded(true);
        for (String entry : entries) {
            item.getChildren().add(new TreeItem<>(prefix + entry));
        }
        root.getChildren().add(item);
    }

    private void updateWarnings(QuestImportSummary summary) {
        List<String> warnings = new ArrayList<>();
        if (summary.warnings() != null) {
            warnings.addAll(summary.warnings());
        }
        if (summary.assetWarnings() != null) {
            warnings.addAll(summary.assetWarnings());
        }
        warningList.setItems(FXCollections.observableArrayList(warnings));
    }

    private String buildSummaryLine(QuestImportSummary summary) {
        return "Chapters added: " + summary.addedChapters().size()
                + ", merged: " + summary.mergedChapters().size()
                + ", quests added: " + summary.addedQuests().size();
    }

    private void onImport(ActionEvent event) {
        if (selectedDirectory == null || previewPack == null) {
            showError("Select a quest pack", "Choose a folder containing questbook/data.snbt before importing.");
            event.consume();
            return;
        }
        try {
            importResult = importService.importPack(selectedDirectory, currentQuestFile, createOptions(copyAssetsCheck.isSelected()));
        } catch (Exception ex) {
            showError("Failed to import SNBT", ex.getMessage());
            event.consume();
        }
    }

    private ImportOptions createOptions(boolean copyAssets) {
        ImportOptions.Builder builder = ImportOptions.builder()
                .chapterPolicy(chapterPolicyCombo.getValue())
                .questPolicy(questPolicyCombo.getValue())
                .copyAssets(copyAssets);
        GroupOption option = groupCombo.getValue();
        if (option != null && option.id != null) {
            builder.targetGroupId(option.id);
        }
        if (copyAssets) {
            builder.assetSource(selectedDirectory.toPath());
            builder.assetDestination(workspace.resolve("assets"));
        }
        return builder.build();
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(getOwner());
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message == null ? "An unknown error occurred." : message);
        alert.showAndWait();
    }

    private record GroupOption(String id, String display) {
        @Override
        public String toString() {
            return display;
        }
    }
}
