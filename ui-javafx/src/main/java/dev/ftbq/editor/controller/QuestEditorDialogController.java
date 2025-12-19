package dev.ftbq.editor.controller;

import dev.ftbq.editor.domain.AdvancementTask;
import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.ItemReward;
import dev.ftbq.editor.domain.ItemTask;
import dev.ftbq.editor.domain.LocationTask;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.Reward;
import dev.ftbq.editor.domain.RewardCommand;
import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.domain.XpLevelReward;
import dev.ftbq.editor.domain.XpReward;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Dialog controller used for creating and editing quests.
 */
public final class QuestEditorDialogController {

    @FXML private Label questIdLabel;
    @FXML private TextField titleField;
    @FXML private TextArea descriptionArea;
    @FXML private TextField iconField;
    @FXML private TextField iconPathField;
    @FXML private ComboBox<Visibility> visibilityComboBox;

    @FXML private ListView<TaskWrapper> taskListView;
    @FXML private Label taskCountLabel;
    @FXML private MenuButton addTaskMenuButton;

    @FXML private ListView<Reward> rewardListView;
    @FXML private Label rewardCountLabel;
    @FXML private MenuButton addRewardMenuButton;

    @FXML private ListView<Dependency> dependencyListView;
    @FXML private Label dependencyCountLabel;

    private final ObservableList<TaskWrapper> tasks = FXCollections.observableArrayList();
    private final ObservableList<Reward> rewards = FXCollections.observableArrayList();
    private final ObservableList<Dependency> dependencies = FXCollections.observableArrayList();

    private Quest quest;
    private boolean saved;

    @FXML
    private void initialize() {
        visibilityComboBox.setItems(FXCollections.observableArrayList(Visibility.values()));
        visibilityComboBox.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Visibility item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatEnumName(item.name()));
            }
        });
        visibilityComboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(Visibility item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "Select visibility" : formatEnumName(item.name()));
            }
        });

        taskListView.setItems(tasks);
        taskListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(TaskWrapper item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.description());
            }
        });

        rewardListView.setItems(rewards);
        rewardListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Reward item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : describeReward(item));
            }
        });

        dependencyListView.setItems(dependencies);
        dependencyListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Dependency item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    String suffix = item.required() ? "required" : "optional";
                    setText(item.questId() + " (" + suffix + ")");
                }
            }
        });

        tasks.addListener((ListChangeListener<TaskWrapper>) change -> updateTaskCount());
        rewards.addListener((ListChangeListener<Reward>) change -> updateRewardCount());
        dependencies.addListener((ListChangeListener<Dependency>) change -> updateDependencyCount());

        updateTaskCount();
        updateRewardCount();
        updateDependencyCount();
    }

    public void setQuest(Quest quest) {
        this.quest = quest;
        loadQuest();
    }

    public boolean wasSaved() {
        return saved;
    }

    public Quest getQuest() {
        return quest;
    }

    private void loadQuest() {
        if (quest == null) {
            return;
        }

        questIdLabel.setText("ID: " + quest.id());
        titleField.setText(quest.title());
        descriptionArea.setText(quest.description());
        iconField.setText(quest.icon().icon());
        iconPathField.setText(quest.icon().relativePath().orElse(""));
        visibilityComboBox.getSelectionModel().select(quest.visibility());

        tasks.setAll(TaskWrapper.wrapAll(quest.tasks()));
        rewards.setAll(quest.rewards());
        dependencies.setAll(quest.dependencies());
    }

    private void updateTaskCount() {
        int count = tasks.size();
        taskCountLabel.setText(count + (count == 1 ? " task" : " tasks"));
    }

    private void updateRewardCount() {
        int count = rewards.size();
        rewardCountLabel.setText(count + (count == 1 ? " reward" : " rewards"));
    }

    private void updateDependencyCount() {
        int count = dependencies.size();
        dependencyCountLabel.setText(count + (count == 1 ? " dependency" : " dependencies"));
    }

    @FXML
    private void onAddItemTask() {
        TextInputDialog dialog = new TextInputDialog("minecraft:stone");
        dialog.setTitle("Add Item Task");
        dialog.setHeaderText("Enter an item ID (optionally with count using 'x', e.g., minecraft:stone x4)");
        dialog.setContentText("Item:");
        dialog.showAndWait().ifPresent(input -> {
            String trimmed = input.trim();
            if (trimmed.isEmpty()) {
                return;
            }
            String itemId = trimmed;
            int count = 1;
            int idx = trimmed.toLowerCase(Locale.ROOT).lastIndexOf('x');
            if (idx > 0) {
                String potentialCount = trimmed.substring(idx + 1).trim();
                try {
                    count = Integer.parseInt(potentialCount);
                    itemId = trimmed.substring(0, idx).trim();
                } catch (NumberFormatException ignored) {
                    itemId = trimmed;
                }
            }
            if (!itemId.isEmpty()) {
                tasks.add(TaskWrapper.fromTask(new ItemTask(new ItemRef(itemId, Math.max(1, count)), true)));
            }
        });
    }

    @FXML
    private void onAddObservationTask() {
        TextInputDialog dialog = new TextInputDialog("minecraft:overworld 0 64 0 5");
        dialog.setTitle("Add Observation Task");
        dialog.setHeaderText("Enter dimension and coordinates (dim x y z radius)");
        dialog.setContentText("Location:");
        dialog.showAndWait().ifPresent(input -> {
            String[] parts = input.trim().split("\\s+");
            if (parts.length >= 4) {
                String dimension = parts[0];
                try {
                    double x = Double.parseDouble(parts[1]);
                    double y = Double.parseDouble(parts[2]);
                    double z = Double.parseDouble(parts[3]);
                    double radius = parts.length >= 5 ? Double.parseDouble(parts[4]) : 5.0;
                    tasks.add(TaskWrapper.fromTask(new LocationTask(dimension, x, y, z, radius)));
                } catch (NumberFormatException ignored) {
                    showValidationError("Invalid coordinates for location task.");
                }
            }
        });
    }

    @FXML
    private void onAddCheckmarkTask() {
        tasks.add(TaskWrapper.fromTask(new AdvancementTask("ftbquests:checkmark")));
    }

    @FXML
    private void onAddAdvancementTask() {
        TextInputDialog dialog = new TextInputDialog("minecraft:story/root");
        dialog.setTitle("Add Advancement Task");
        dialog.setHeaderText("Enter advancement ID");
        dialog.setContentText("Advancement:");
        dialog.showAndWait().ifPresent(input -> {
            String trimmed = input.trim();
            if (!trimmed.isEmpty()) {
                tasks.add(TaskWrapper.fromTask(new AdvancementTask(trimmed)));
            }
        });
    }

    @FXML
    private void onAddStatTask() {
        TextInputDialog dialog = new TextInputDialog("minecraft:custom/stat");
        dialog.setTitle("Add Stat Task");
        dialog.setHeaderText("Enter statistic identifier");
        dialog.setContentText("Statistic:");
        dialog.showAndWait().ifPresent(input -> {
            String trimmed = input.trim();
            if (!trimmed.isEmpty()) {
                tasks.add(TaskWrapper.fromTask(new AdvancementTask("stat:" + trimmed)));
            }
        });
    }

    @FXML
    private void onEditTask() {
        TaskWrapper selected = taskListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        switch (selected.kind()) {
            case ITEM -> editItemTask(selected);
            case LOCATION -> editLocationTask(selected);
            case ADVANCEMENT -> editAdvancementTask(selected);
        }
    }

    private void editItemTask(TaskWrapper wrapper) {
        ItemTask task = (ItemTask) wrapper.task();
        TextInputDialog dialog = new TextInputDialog(task.item().itemId() + " x" + task.item().count());
        dialog.setTitle("Edit Item Task");
        dialog.setHeaderText("Update item ID and optional count");
        dialog.setContentText("Item:");
        dialog.showAndWait().ifPresent(input -> {
            String trimmed = input.trim();
            if (trimmed.isEmpty()) {
                return;
            }
            String itemId = trimmed;
            int count = task.item().count();
            int idx = trimmed.toLowerCase(Locale.ROOT).lastIndexOf('x');
            if (idx > 0) {
                String potentialCount = trimmed.substring(idx + 1).trim();
                try {
                    count = Integer.parseInt(potentialCount);
                    itemId = trimmed.substring(0, idx).trim();
                } catch (NumberFormatException ignored) {
                    itemId = trimmed;
                }
            }
            ItemTask updated = new ItemTask(new ItemRef(itemId, Math.max(1, count)), task.consume());
            replaceTask(wrapper, updated);
        });
    }

    private void editLocationTask(TaskWrapper wrapper) {
        LocationTask task = (LocationTask) wrapper.task();
        String preset = "%s %.2f %.2f %.2f %.2f".formatted(task.dimension(), task.x(), task.y(), task.z(), task.radius());
        TextInputDialog dialog = new TextInputDialog(preset);
        dialog.setTitle("Edit Observation Task");
        dialog.setHeaderText("Update dimension and coordinates (dim x y z radius)");
        dialog.setContentText("Location:");
        dialog.showAndWait().ifPresent(input -> {
            String[] parts = input.trim().split("\\s+");
            if (parts.length >= 4) {
                try {
                    String dimension = parts[0];
                    double x = Double.parseDouble(parts[1]);
                    double y = Double.parseDouble(parts[2]);
                    double z = Double.parseDouble(parts[3]);
                    double radius = parts.length >= 5 ? Double.parseDouble(parts[4]) : task.radius();
                    LocationTask updated = new LocationTask(dimension, x, y, z, radius);
                    replaceTask(wrapper, updated);
                } catch (NumberFormatException ignored) {
                    showValidationError("Invalid coordinates for location task.");
                }
            }
        });
    }

    private void editAdvancementTask(TaskWrapper wrapper) {
        AdvancementTask task = (AdvancementTask) wrapper.task();
        TextInputDialog dialog = new TextInputDialog(task.advancementId());
        dialog.setTitle("Edit Advancement Task");
        dialog.setHeaderText("Update advancement identifier");
        dialog.setContentText("Advancement:");
        dialog.showAndWait().ifPresent(input -> {
            String trimmed = input.trim();
            if (!trimmed.isEmpty()) {
                replaceTask(wrapper, new AdvancementTask(trimmed));
            }
        });
    }

    private void replaceTask(TaskWrapper wrapper, dev.ftbq.editor.domain.Task updated) {
        int index = tasks.indexOf(wrapper);
        if (index >= 0) {
            tasks.set(index, TaskWrapper.fromTask(updated));
        }
    }

    @FXML
    private void onRemoveTask() {
        TaskWrapper selected = taskListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            tasks.remove(selected);
        }
    }

    @FXML
    private void onMoveTaskUp() {
        int index = taskListView.getSelectionModel().getSelectedIndex();
        if (index > 0) {
            TaskWrapper task = tasks.remove(index);
            tasks.add(index - 1, task);
            taskListView.getSelectionModel().select(index - 1);
        }
    }

    @FXML
    private void onMoveTaskDown() {
        int index = taskListView.getSelectionModel().getSelectedIndex();
        if (index >= 0 && index < tasks.size() - 1) {
            TaskWrapper task = tasks.remove(index);
            tasks.add(index + 1, task);
            taskListView.getSelectionModel().select(index + 1);
        }
    }

    @FXML
    private void onAddItemReward() {
        TextInputDialog dialog = new TextInputDialog("minecraft:diamond x1");
        dialog.setTitle("Add Item Reward");
        dialog.setHeaderText("Enter item ID and optional count (id xcount)");
        dialog.setContentText("Item:");
        dialog.showAndWait().ifPresent(input -> {
            String trimmed = input.trim();
            if (trimmed.isEmpty()) {
                return;
            }
            String itemId = trimmed;
            int count = 1;
            int idx = trimmed.toLowerCase(Locale.ROOT).lastIndexOf('x');
            if (idx > 0) {
                try {
                    count = Integer.parseInt(trimmed.substring(idx + 1).trim());
                    itemId = trimmed.substring(0, idx).trim();
                } catch (NumberFormatException ignored) {
                    itemId = trimmed;
                }
            }
            if (!itemId.isEmpty()) {
                rewards.add(new ItemReward(new ItemRef(itemId, Math.max(1, count))));
            }
        });
    }

    @FXML
    private void onAddCurrencyReward() {
        TextInputDialog dialog = new TextInputDialog("25");
        dialog.setTitle("Add Currency Reward");
        dialog.setHeaderText("Enter coin amount (creates a command reward)");
        dialog.setContentText("Amount:");
        dialog.showAndWait().ifPresent(input -> {
            String trimmed = input.trim();
            if (trimmed.isEmpty()) {
                return;
            }
            try {
                int amount = Integer.parseInt(trimmed);
                String command = "/ftbquests addcoins @p " + Math.max(0, amount);
                rewards.add(Reward.command(new RewardCommand(command, true)));
            } catch (NumberFormatException ignored) {
                showValidationError("Currency amount must be a whole number.");
            }
        });
    }

    @FXML
    private void onAddXPReward() {
        TextInputDialog dialog = new TextInputDialog("100");
        dialog.setTitle("Add XP Reward");
        dialog.setHeaderText("Enter experience amount");
        dialog.setContentText("XP amount:");
        dialog.showAndWait().ifPresent(input -> {
            String trimmed = input.trim();
            if (trimmed.isEmpty()) {
                return;
            }
            try {
                int amount = Integer.parseInt(trimmed);
                rewards.add(new XpReward(Math.max(0, amount)));
            } catch (NumberFormatException ignored) {
                showValidationError("XP amount must be a whole number.");
            }
        });
    }

    @FXML
    private void onAddCommandReward() {
        TextInputDialog dialog = new TextInputDialog("say Quest completed!");
        dialog.setTitle("Add Command Reward");
        dialog.setHeaderText("Enter command to run when quest completes");
        dialog.setContentText("Command:");
        dialog.showAndWait().ifPresent(input -> {
            String trimmed = input.trim();
            if (!trimmed.isEmpty()) {
                ChoiceDialog<String> executorDialog = new ChoiceDialog<>("Server", "Server", "Player");
                executorDialog.setTitle("Command Executor");
                executorDialog.setHeaderText("Run command as server or player?");
                executorDialog.setContentText("Executor:");
                boolean runAsServer = executorDialog.showAndWait().map("Server"::equals).orElse(true);
                rewards.add(Reward.command(new RewardCommand(trimmed, runAsServer)));
            }
        });
    }

    @FXML
    private void onAddLootTableReward() {
        TextInputDialog dialog = new TextInputDialog("minecraft:chests/simple_dungeon");
        dialog.setTitle("Add Loot Table Reward");
        dialog.setHeaderText("Enter loot table ID");
        dialog.setContentText("Loot table:");
        dialog.showAndWait().ifPresent(input -> {
            String trimmed = input.trim();
            if (!trimmed.isEmpty()) {
                rewards.add(Reward.lootTable(trimmed));
            }
        });
    }

    @FXML
    private void onEditReward() {
        Reward selected = rewardListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        switch (selected.type()) {
            case ITEM -> editItemReward(selected);
            case XP_AMOUNT -> editXpReward(selected);
            case XP_LEVELS -> editXpLevelReward(selected);
            case LOOT_TABLE -> editLootTableReward(selected);
            case COMMAND -> editCommandReward(selected);
        }
    }

    private void editItemReward(Reward reward) {
        ItemReward itemReward = (ItemReward) reward;
        String preset = itemReward.item().map(item -> item.itemId() + " x" + item.count()).orElse("minecraft:diamond x1");
        TextInputDialog dialog = new TextInputDialog(preset);
        dialog.setTitle("Edit Item Reward");
        dialog.setHeaderText("Update item ID and optional count");
        dialog.setContentText("Item:");
        dialog.showAndWait().ifPresent(input -> {
            String trimmed = input.trim();
            if (trimmed.isEmpty()) {
                return;
            }
            String itemId = trimmed;
            int count = itemReward.item().map(ItemRef::count).orElse(1);
            int idx = trimmed.toLowerCase(Locale.ROOT).lastIndexOf('x');
            if (idx > 0) {
                try {
                    count = Integer.parseInt(trimmed.substring(idx + 1).trim());
                    itemId = trimmed.substring(0, idx).trim();
                } catch (NumberFormatException ignored) {
                    itemId = trimmed;
                }
            }
            ItemReward updated = new ItemReward(new ItemRef(itemId, Math.max(1, count)));
            replaceReward(reward, updated);
        });
    }

    private void editXpReward(Reward reward) {
        int current = reward.experienceAmount().orElse(0);
        TextInputDialog dialog = new TextInputDialog(Integer.toString(current));
        dialog.setTitle("Edit XP Reward");
        dialog.setHeaderText("Update experience amount");
        dialog.setContentText("XP amount:");
        dialog.showAndWait().ifPresent(input -> {
            try {
                int amount = Integer.parseInt(input.trim());
                replaceReward(reward, new XpReward(Math.max(0, amount)));
            } catch (NumberFormatException ignored) {
                showValidationError("XP amount must be a whole number.");
            }
        });
    }

    private void editXpLevelReward(Reward reward) {
        int current = reward.experienceLevels().orElse(0);
        TextInputDialog dialog = new TextInputDialog(Integer.toString(current));
        dialog.setTitle("Edit XP Level Reward");
        dialog.setHeaderText("Update experience levels");
        dialog.setContentText("Levels:");
        dialog.showAndWait().ifPresent(input -> {
            try {
                int levels = Integer.parseInt(input.trim());
                replaceReward(reward, new XpLevelReward(Math.max(0, levels)));
            } catch (NumberFormatException ignored) {
                showValidationError("XP levels must be a whole number.");
            }
        });
    }

    private void editLootTableReward(Reward reward) {
        String current = reward.lootTableId().orElse("");
        TextInputDialog dialog = new TextInputDialog(current);
        dialog.setTitle("Edit Loot Table Reward");
        dialog.setHeaderText("Update loot table identifier");
        dialog.setContentText("Loot table:");
        dialog.showAndWait().ifPresent(input -> {
            String trimmed = input.trim();
            if (!trimmed.isEmpty()) {
                replaceReward(reward, Reward.lootTable(trimmed));
            }
        });
    }

    private void editCommandReward(Reward reward) {
        RewardCommand command = reward.command().orElse(new RewardCommand("say Quest completed!", true));
        TextInputDialog dialog = new TextInputDialog(command.command());
        dialog.setTitle("Edit Command Reward");
        dialog.setHeaderText("Update command to execute");
        dialog.setContentText("Command:");
        Optional<String> commandText = dialog.showAndWait();
        if (commandText.isEmpty() || commandText.get().trim().isEmpty()) {
            return;
        }
        ChoiceDialog<String> executorDialog = new ChoiceDialog<>(command.runAsServer() ? "Server" : "Player", "Server", "Player");
        executorDialog.setTitle("Command Executor");
        executorDialog.setHeaderText("Run command as server or player?");
        executorDialog.setContentText("Executor:");
        boolean runAsServer = executorDialog.showAndWait().map("Server"::equals).orElse(command.runAsServer());
        replaceReward(reward, Reward.command(new RewardCommand(commandText.get().trim(), runAsServer)));
    }

    private void replaceReward(Reward existing, Reward updated) {
        int index = rewards.indexOf(existing);
        if (index >= 0) {
            rewards.set(index, updated);
        }
    }

    @FXML
    private void onRemoveReward() {
        Reward selected = rewardListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            rewards.remove(selected);
        }
    }

    @FXML
    private void onMoveRewardUp() {
        int index = rewardListView.getSelectionModel().getSelectedIndex();
        if (index > 0) {
            Reward reward = rewards.remove(index);
            rewards.add(index - 1, reward);
            rewardListView.getSelectionModel().select(index - 1);
        }
    }

    @FXML
    private void onMoveRewardDown() {
        int index = rewardListView.getSelectionModel().getSelectedIndex();
        if (index >= 0 && index < rewards.size() - 1) {
            Reward reward = rewards.remove(index);
            rewards.add(index + 1, reward);
            rewardListView.getSelectionModel().select(index + 1);
        }
    }

    @FXML
    private void onAddDependency() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Dependency");
        dialog.setHeaderText("Enter quest ID to depend on");
        dialog.setContentText("Quest ID:");
        Optional<String> questId = dialog.showAndWait().map(String::trim).filter(id -> !id.isEmpty());
        if (questId.isEmpty()) {
            return;
        }
        Alert requiredDialog = new Alert(Alert.AlertType.CONFIRMATION);
        requiredDialog.setTitle("Dependency Type");
        requiredDialog.setHeaderText("Should this dependency be required?");
        requiredDialog.setContentText("Choose OK for required or Cancel for optional.");
        boolean required = requiredDialog.showAndWait().map(ButtonType.OK::equals).orElse(false);
        dependencies.add(new Dependency(questId.get(), required));
    }

    @FXML
    private void onRemoveDependency() {
        Dependency selected = dependencyListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            dependencies.remove(selected);
        }
    }

    @FXML
    private void onSave() {
        String title = titleField.getText() == null ? "" : titleField.getText().trim();
        if (title.isEmpty()) {
            showValidationError("Quest title is required.");
            return;
        }

        String description = descriptionArea.getText() == null ? "" : descriptionArea.getText().trim();
        String iconId = iconField.getText() == null || iconField.getText().trim().isEmpty()
                ? "minecraft:book"
                : iconField.getText().trim();
        String iconPath = iconPathField.getText() == null ? "" : iconPathField.getText().trim();
        Visibility visibility = Optional.ofNullable(visibilityComboBox.getValue()).orElse(Visibility.VISIBLE);

        Quest.Builder builder = Quest.builder()
                .id(quest == null || quest.id() == null ? generateQuestId() : quest.id())
                .title(title)
                .description(description)
                .icon(iconPath.isEmpty() ? new IconRef(iconId) : new IconRef(iconId, Optional.of(iconPath)))
                .visibility(visibility)
                .tasks(TaskWrapper.unwrapAll(tasks))
                .rewards(new ArrayList<>(rewards))
                .dependencies(new ArrayList<>(dependencies));

        quest = builder.build();
        saved = true;
        closeDialog();
    }

    @FXML
    private void onCancel() {
        saved = false;
        closeDialog();
    }

    private void closeDialog() {
        Stage stage = (Stage) questIdLabel.getScene().getWindow();
        stage.close();
    }

    private void showValidationError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Invalid Input");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private static String describeReward(Reward reward) {
        return switch (reward.type()) {
            case ITEM -> reward.item().map(item -> item.count() + " × " + item.itemId()).orElse("Item reward");
            case XP_AMOUNT -> reward.experienceAmount().map(amount -> amount + " XP").orElse("XP reward");
            case XP_LEVELS -> reward.experienceLevels().map(levels -> levels + " levels").orElse("XP levels");
            case LOOT_TABLE -> reward.lootTableId().orElse("Loot table");
            case COMMAND -> reward.command().map(RewardCommand::command).orElse("Command reward");
        };
    }

    private static String formatEnumName(String name) {
        String lower = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String generateQuestId() {
        return UUID.randomUUID().toString();
    }

    private record TaskWrapper(dev.ftbq.editor.domain.Task task, TaskKind kind) {
        static TaskWrapper fromTask(dev.ftbq.editor.domain.Task task) {
            return new TaskWrapper(task, determineKind(task));
        }

        static List<TaskWrapper> wrapAll(List<? extends dev.ftbq.editor.domain.Task> tasks) {
            List<TaskWrapper> wrapped = new ArrayList<>(tasks.size());
            for (dev.ftbq.editor.domain.Task task : tasks) {
                wrapped.add(fromTask(task));
            }
            return wrapped;
        }

        static List<dev.ftbq.editor.domain.Task> unwrapAll(List<TaskWrapper> tasks) {
            List<dev.ftbq.editor.domain.Task> unwrapped = new ArrayList<>(tasks.size());
            for (TaskWrapper wrapper : tasks) {
                unwrapped.add(wrapper.task());
            }
            return unwrapped;
        }

        String description() {
            return switch (kind) {
                case ITEM -> task.describe();
                case ADVANCEMENT -> task.describe();
                case LOCATION -> task.describe();
            };
        }

        private static TaskKind determineKind(dev.ftbq.editor.domain.Task task) {
            if (task instanceof ItemTask) {
                return TaskKind.ITEM;
            } else if (task instanceof LocationTask) {
                return TaskKind.LOCATION;
            }
            return TaskKind.ADVANCEMENT;
        }
    }

    private enum TaskKind {
        ITEM,
        ADVANCEMENT,
        LOCATION
    }
}
