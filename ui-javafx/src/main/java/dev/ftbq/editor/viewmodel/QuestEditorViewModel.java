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
import dev.ftbq.editor.store.StoreDao;
import dev.ftbq.editor.support.UiServiceLocator;
import dev.ftbq.editor.viewmodel.commands.QuestFieldChangeCommand;
import dev.ftbq.editor.viewmodel.commands.QuestFieldChangeCommand.Field;
import dev.ftbq.editor.viewmodel.commands.QuestListChangeCommand;
import dev.ftbq.editor.viewmodel.commands.QuestListChangeCommand.CollectionType;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ListChangeListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

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
    private List<Task> taskSnapshot = List.of();
    private List<Reward> rewardSnapshot = List.of();
    private List<Dependency> dependencySnapshot = List.of();

    public QuestEditorViewModel(CommandBus commandBus, EventBus eventBus, UndoManager undoManager) {
        this.commandBus = Objects.requireNonNull(commandBus, "commandBus");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.undoManager = Objects.requireNonNull(undoManager, "undoManager");
        attachListeners();
        attachCollectionListeners();
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

    public StringProperty titleProperty() { return title; }
    public StringProperty descriptionProperty() { return description; }
    public ObjectProperty<IconRef> iconProperty() { return icon; }
    public ObservableList<Task> getTasks() { return tasks; }
    public ObservableList<Reward> getRewards() { return rewards; }
    public ObservableList<Dependency> getDependencies() { return dependencies; }
    public Quest getCurrentQuest() { return currentQuest; }

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
            updateSnapshots();
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

        builder.tasks(new ArrayList<>(tasks));
        builder.rewards(new ArrayList<>(rewards));
        builder.dependencies(new ArrayList<>(dependencies));

        return builder.build();
    }

    public Quest save() {
        Quest savedQuest = toQuest();
        currentQuest = savedQuest;
        StoreDao storeDao = UiServiceLocator.getStoreDao();
        if (storeDao != null) {
            storeDao.saveQuest(savedQuest);
        }
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

    private void attachCollectionListeners() {
        tasks.addListener((ListChangeListener<Task>) change -> onCollectionChanged(
                CollectionType.TASKS,
                () -> taskSnapshot,
                () -> new ArrayList<>(tasks)
        ));
        rewards.addListener((ListChangeListener<Reward>) change -> onCollectionChanged(
                CollectionType.REWARDS,
                () -> rewardSnapshot,
                () -> new ArrayList<>(rewards)
        ));
        dependencies.addListener((ListChangeListener<Dependency>) change -> onCollectionChanged(
                CollectionType.DEPENDENCIES,
                () -> dependencySnapshot,
                () -> new ArrayList<>(dependencies)
        ));
    }

    private void registerCommandHandlers() {
        if (commandBus == null || commandSubscriptionRegistered) {
            return;
        }
        // Updated for new CommandBus API
        commandBus.subscribeAll(command -> {
            if (command instanceof QuestFieldChangeCommand changeCommand) {
                handleFieldChangeCommand(changeCommand);
            } else if (command instanceof QuestListChangeCommand listChangeCommand) {
                handleListChangeCommand(listChangeCommand);
            }
        });
        commandSubscriptionRegistered = true;
    }

    private void registerUndoFactories() {
        if (undoManager != null) {
            undoManager.registerFactory(QuestFieldChangeCommand.TYPE, QuestFieldChangeCommand::fromPayload);
            undoManager.registerFactory(QuestListChangeCommand.TYPE, QuestListChangeCommand::fromPayload);
        }
    }

    private void onFieldChanged(Field field, String oldValue, String newValue) {
        if (suppressRecording || Objects.equals(oldValue, newValue) || questId == null || questId.isBlank() || commandBus == null) {
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

    private void handleListChangeCommand(QuestListChangeCommand command) {
        if (!Objects.equals(questId, command.questId())) {
            return;
        }
        boolean previous = suppressRecording;
        suppressRecording = true;
        try {
            switch (command.collectionType()) {
                case TASKS -> {
                    tasks.setAll(command.tasks());
                    taskSnapshot = List.copyOf(command.tasks());
                }
                case REWARDS -> {
                    rewards.setAll(command.rewards());
                    rewardSnapshot = List.copyOf(command.rewards());
                }
                case DEPENDENCIES -> {
                    dependencies.setAll(command.dependencies());
                    dependencySnapshot = List.copyOf(command.dependencies());
                }
            }
            currentQuest = toQuest();
        } finally {
            suppressRecording = previous;
        }
    }

    private void onCollectionChanged(CollectionType type,
                                     Supplier<List<?>> previousSupplier,
                                     Supplier<List<?>> currentSupplier) {
        if (suppressRecording || questId == null || questId.isBlank() || commandBus == null) {
            updateSnapshot(type, currentSupplier.get());
            return;
        }
        List<?> previous = previousSupplier.get();
        List<?> current = currentSupplier.get();
        if (Objects.equals(previous, current)) {
            updateSnapshot(type, current);
            return;
        }
        QuestListChangeCommand command = switch (type) {
            case TASKS -> QuestListChangeCommand.forTasks(questId, castList(current), castList(previous));
            case REWARDS -> QuestListChangeCommand.forRewards(questId, castList(current), castList(previous));
            case DEPENDENCIES -> QuestListChangeCommand.forDependencies(questId, castList(current), castList(previous));
        };
        boolean previousRecording = suppressRecording;
        suppressRecording = true;
        try {
            commandBus.dispatch(command);
        } finally {
            suppressRecording = previousRecording;
        }
        updateSnapshot(type, current);
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> castList(List<?> list) {
        return (List<T>) list;
    }

    private void updateSnapshot(CollectionType type, List<?> snapshot) {
        switch (type) {
            case TASKS -> taskSnapshot = List.copyOf(castList(snapshot));
            case REWARDS -> rewardSnapshot = List.copyOf(castList(snapshot));
            case DEPENDENCIES -> dependencySnapshot = List.copyOf(castList(snapshot));
        }
    }

    private void updateSnapshots() {
        taskSnapshot = List.copyOf(tasks);
        rewardSnapshot = List.copyOf(rewards);
        dependencySnapshot = List.copyOf(dependencies);
    }
}
