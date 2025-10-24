package dev.ftbq.editor.viewmodel;

import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.Reward;
import dev.ftbq.editor.domain.Task;
import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.services.bus.CommandBus;
import dev.ftbq.editor.services.bus.EventBus;
import dev.ftbq.editor.services.bus.UndoManager;
import dev.ftbq.editor.services.events.QuestChanged;
import dev.ftbq.editor.viewmodel.commands.QuestFieldChangeCommand;
import dev.ftbq.editor.viewmodel.commands.QuestFieldChangeCommand.Field;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class QuestEditorViewModel {
    private final StringProperty title = new SimpleStringProperty("");
    private final StringProperty description = new SimpleStringProperty("");
    private final ObjectProperty<IconRef> icon = new SimpleObjectProperty<>(new IconRef("minecraft:book"));
    private final ObservableList<Task> tasks = FXCollections.observableArrayList();
    private final ObservableList<Reward> rewards = FXCollections.observableArrayList();
    private final ObservableList<Dependency> dependencies = FXCollections.observableArrayList();

    private CommandBus commandBus;
    private EventBus eventBus;
    private UndoManager undoManager;

    private String questId = "";
    private Visibility visibility = Visibility.VISIBLE;
    private Quest currentQuest;
    private boolean suppressRecording;
    private boolean commandSubscriptionRegistered;

    public QuestEditorViewModel(CommandBus commandBus, EventBus eventBus, UndoManager undoManager) {
        this.commandBus = Objects.requireNonNull(commandBus, "commandBus");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.undoManager = Objects.requireNonNull(undoManager, "undoManager");
        attachListeners();
        registerCommandHandlers();
        registerUndoFactories();
    }

    public void setCommandBus(CommandBus commandBus) {
        this.commandBus = Objects.requireNonNull(commandBus, "commandBus");
        if (!commandSubscriptionRegistered) {
            registerCommandHandlers();
        }
    }

    public void setEventBus(EventBus eventBus) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    public void setUndoManager(UndoManager undoManager) {
        this.undoManager = Objects.requireNonNull(undoManager, "undoManager");
        registerUndoFactories();
    }

    public StringProperty titleProperty() {
        return title;
    }

    public StringProperty descriptionProperty() {
        return description;
    }

    public ObjectProperty<IconRef> iconProperty() {
        return icon;
    }

    public ObservableList<Task> getTasks() {
        return tasks;
    }

    public ObservableList<Reward> getRewards() {
        return rewards;
    }

    public ObservableList<Dependency> getDependencies() {
        return dependencies;
    }

    public Quest getCurrentQuest() {
        return currentQuest;
    }

    public void loadQuest(Quest quest) {
        Objects.requireNonNull(quest, "quest");
        suppressRecording = true;
        try {
            currentQuest = quest;
            questId = quest.id();
            title.set(quest.title());
            description.set(quest.description());
            icon.set(quest.icon());
            visibility = quest.visibility();
            tasks.setAll(quest.tasks());
            rewards.setAll(quest.rewards());
            dependencies.setAll(quest.dependencies());
        } finally {
            suppressRecording = false;
        }
    }

    public Quest toQuest() {
        IconRef currentIcon = icon.get();
        if (currentIcon == null) {
            currentIcon = new IconRef("minecraft:book");
        }

        Quest.Builder builder = Quest.builder()
                .id(questId)
                .title(title.get())
                .description(description.get())
                .icon(currentIcon)
                .visibility(visibility);

        List<Task> updatedTasks = new ArrayList<>(tasks);
        builder.tasks(updatedTasks);

        List<Reward> updatedRewards = new ArrayList<>(rewards);
        builder.rewards(updatedRewards);

        List<Dependency> updatedDependencies = new ArrayList<>(dependencies);
        builder.dependencies(updatedDependencies);

        return builder.build();
    }

    public Quest save() {
        Quest savedQuest = toQuest();
        currentQuest = savedQuest;
        if (eventBus != null) {
            eventBus.publish(new QuestChanged(savedQuest));
        }
        return savedQuest;
    }

    public void revertChanges() {
        if (currentQuest != null) {
            loadQuest(currentQuest);
        }
    }

    private void attachListeners() {
        title.addListener((obs, oldValue, newValue) -> onFieldChanged(Field.TITLE, oldValue, newValue));
        description.addListener((obs, oldValue, newValue) -> onFieldChanged(Field.DESCRIPTION, oldValue, newValue));
    }

    private void registerCommandHandlers() {
        if (commandBus == null || commandSubscriptionRegistered) {
            return;
        }
        commandBus.subscribe(QuestFieldChangeCommand.class, this::handleFieldChangeCommand);
        commandSubscriptionRegistered = true;
    }

    private void registerUndoFactories() {
        if (undoManager != null) {
            undoManager.registerFactory(QuestFieldChangeCommand.TYPE, QuestFieldChangeCommand::fromPayload);
        }
    }

    private void onFieldChanged(Field field, String oldValue, String newValue) {
        if (suppressRecording) {
            return;
        }
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        if (questId == null || questId.isBlank()) {
            return;
        }
        if (commandBus == null) {
            return;
        }
        QuestFieldChangeCommand command = new QuestFieldChangeCommand(questId, field, newValue, oldValue);
        boolean previous = suppressRecording;
        suppressRecording = true;
        try {
            commandBus.dispatch(command);
        } finally {
            suppressRecording = previous;
        }
    }

    private void handleFieldChangeCommand(QuestFieldChangeCommand command) {
        if (!Objects.equals(questId, command.questId())) {
            return;
        }
        boolean previous = suppressRecording;
        suppressRecording = true;
        try {
            switch (command.field()) {
                case TITLE -> title.set(command.value());
                case DESCRIPTION -> description.set(command.value());
            }
            currentQuest = toQuest();
        } finally {
            suppressRecording = previous;
        }
    }
}
