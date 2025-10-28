package dev.ftbq.editor.ui.model;

import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.services.generator.RewardConfiguration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

/**
 * Presentation model describing reward configuration preferences for AI generation.
 */
public final class RewardSelectionModel {

    private final BooleanProperty itemRewardsEnabled = new SimpleBooleanProperty(true);
    private final BooleanProperty xpRewardsEnabled = new SimpleBooleanProperty(true);
    private final BooleanProperty lootTableRewardsEnabled = new SimpleBooleanProperty(true);
    private final ObservableList<String> availableLootTables = FXCollections.observableArrayList();
    private final ReadOnlyListWrapper<String> selectedLootTables = new ReadOnlyListWrapper<>(FXCollections.observableArrayList());
    private final StringProperty summary = new SimpleStringProperty();

    public RewardSelectionModel() {
        summary.set(buildSummary());
        itemRewardsEnabled.addListener((obs, old, value) -> summary.set(buildSummary()));
        xpRewardsEnabled.addListener((obs, old, value) -> summary.set(buildSummary()));
        lootTableRewardsEnabled.addListener((obs, old, value) -> {
            if (!Boolean.TRUE.equals(value)) {
                selectedLootTables.clear();
            }
            summary.set(buildSummary());
        });
        selectedLootTables.addListener((ListChangeListener<String>) change -> summary.set(buildSummary()));
    }

    public BooleanProperty itemRewardsEnabledProperty() {
        return itemRewardsEnabled;
    }

    public BooleanProperty xpRewardsEnabledProperty() {
        return xpRewardsEnabled;
    }

    public BooleanProperty lootTableRewardsEnabledProperty() {
        return lootTableRewardsEnabled;
    }

    public ObservableList<String> availableLootTables() {
        return FXCollections.unmodifiableObservableList(availableLootTables);
    }

    public ObservableList<String> selectedLootTables() {
        return selectedLootTables.getReadOnlyProperty();
    }

    public StringProperty summaryProperty() {
        return summary;
    }

    public void setAvailableLootTables(Collection<LootTable> lootTables) {
        availableLootTables.clear();
        if (lootTables == null) {
            summary.set(buildSummary());
            return;
        }
        List<String> ids = new ArrayList<>();
        for (LootTable table : lootTables) {
            if (table == null || table.id() == null || table.id().isBlank()) {
                continue;
            }
            ids.add(table.id());
        }
        ids.sort(Comparator.naturalOrder());
        availableLootTables.addAll(ids);
        syncSelectedWithAvailable();
        summary.set(buildSummary());
    }

    public void toggleLootTable(String lootTableId) {
        Objects.requireNonNull(lootTableId, "lootTableId");
        if (!lootTableRewardsEnabled.get()) {
            return;
        }
        if (selectedLootTables.contains(lootTableId)) {
            selectedLootTables.remove(lootTableId);
        } else if (availableLootTables.contains(lootTableId)) {
            selectedLootTables.add(lootTableId);
        }
    }

    public RewardConfiguration toConfiguration() {
        return new RewardConfiguration(
                itemRewardsEnabled.get(),
                xpRewardsEnabled.get(),
                lootTableRewardsEnabled.get(),
                new ArrayList<>(selectedLootTables));
    }

    private void syncSelectedWithAvailable() {
        if (selectedLootTables.isEmpty()) {
            return;
        }
        Set<String> available = new LinkedHashSet<>(availableLootTables);
        selectedLootTables.removeIf(id -> !available.contains(id));
    }

    private String buildSummary() {
        StringBuilder builder = new StringBuilder("Rewards (");
        builder.append(itemRewardsEnabled.get() ? "items✔" : "items✖");
        builder.append(" · ");
        builder.append(xpRewardsEnabled.get() ? "xp✔" : "xp✖");
        builder.append(" · ");
        builder.append(lootTableRewardsEnabled.get() ? "loot✔" : "loot✖");
        if (lootTableRewardsEnabled.get()) {
            builder.append(" ");
            builder.append('[').append(selectedLootTables.size()).append('/').append(availableLootTables.size()).append(']');
        }
        builder.append(')');
        return builder.toString();
    }
}
