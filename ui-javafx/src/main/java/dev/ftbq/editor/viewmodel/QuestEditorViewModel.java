package dev.ftbq.editor.viewmodel;

import dev.ftbq.editor.domain.Dependency;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.Reward;
import dev.ftbq.editor.domain.Task;
import dev.ftbq.editor.domain.Visibility;
import dev.ftbq.editor.services.CommandBus;
import dev.ftbq.editor.services.events.QuestChanged;
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

    private String questId = "";
    private Visibility visibility = Visibility.VISIBLE;
    private Quest currentQuest;

    public QuestEditorViewModel(CommandBus commandBus) {
        this.commandBus = Objects.requireNonNull(commandBus, "commandBus");
    }

    public void setCommandBus(CommandBus commandBus) {
        this.commandBus = Objects.requireNonNull(commandBus, "commandBus");
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
        currentQuest = quest;
        questId = quest.id();
        title.set(quest.title());
        description.set(quest.description());
        icon.set(quest.icon());
        visibility = quest.visibility();
        tasks.setAll(quest.tasks());
        rewards.setAll(quest.rewards());
        dependencies.setAll(quest.dependencies());
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
        commandBus.publish(new QuestChanged(savedQuest));
        return savedQuest;
    }

    public void revertChanges() {
        if (currentQuest != null) {
            loadQuest(currentQuest);
        }
    }
}
