package dev.ftbq.editor.ui.model;

import dev.ftbq.editor.services.mods.ModRegistryService;
import dev.ftbq.editor.services.mods.RegisteredMod;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Presentation model backing the mod multi-select dropdown in the UI.
 */
public final class ModSelectionModel {

    private final ObservableList<ModOption> options = FXCollections.observableArrayList();
    private final ObservableList<RegisteredMod> selectedBacking = FXCollections.observableArrayList();
    private final ReadOnlyListWrapper<RegisteredMod> selectedMods = new ReadOnlyListWrapper<>(selectedBacking);
    private final StringProperty summary = new SimpleStringProperty("Mods (0)");
    private final StringProperty warningMessage = new SimpleStringProperty();
    private final IntegerProperty selectionLimit = new SimpleIntegerProperty(ModRegistryService.MAX_SELECTION);
    private boolean internalChange;

    public ObservableList<ModOption> options() {
        return options;
    }

    public ObservableList<RegisteredMod> selectedMods() {
        return selectedMods.getReadOnlyProperty();
    }

    public List<RegisteredMod> selectedModsSnapshot() {
        return List.copyOf(selectedBacking);
    }

    public StringProperty summaryProperty() {
        return summary;
    }

    public StringProperty warningMessageProperty() {
        return warningMessage;
    }

    public int getSelectionLimit() {
        return selectionLimit.get();
    }

    public void setSelectionLimit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("selectionLimit must be positive");
        }
        selectionLimit.set(limit);
        trimSelectionIfNeeded();
    }

    public IntegerProperty selectionLimitProperty() {
        return selectionLimit;
    }

    public void setAvailableMods(Collection<RegisteredMod> mods) {
        Set<String> previouslySelected = new HashSet<>();
        for (RegisteredMod mod : selectedBacking) {
            previouslySelected.add(normalizeKey(mod.modId()));
        }

        options.clear();
        selectedBacking.clear();
        warningMessage.set(null);

        if (mods == null || mods.isEmpty()) {
            updateSummary();
            return;
        }

        List<RegisteredMod> sorted = new ArrayList<>(mods);
        sorted.sort(Comparator.comparing(RegisteredMod::displayName, String.CASE_INSENSITIVE_ORDER));

        for (RegisteredMod mod : sorted) {
            ModOption option = new ModOption(mod);
            option.selectedProperty().addListener((obs, oldVal, newVal) -> onOptionSelectionChanged(option, newVal));
            options.add(option);
        }

        for (ModOption option : options) {
            if (previouslySelected.contains(normalizeKey(option.mod().modId()))
                    && selectedBacking.size() < getSelectionLimit()) {
                internalChange = true;
                option.setSelected(true);
                internalChange = false;
                if (!selectedBacking.contains(option.mod())) {
                    selectedBacking.add(option.mod());
                }
            }
        }

        updateSummary();
    }

    public boolean toggle(ModOption option) {
        if (option == null) {
            return false;
        }
        return setOptionSelected(option, !option.isSelected());
    }

    public boolean setOptionSelected(ModOption option, boolean selected) {
        Objects.requireNonNull(option, "option");
        if (option.isSelected() == selected) {
            return true;
        }
        if (selected && selectedBacking.size() >= getSelectionLimit()) {
            warningMessage.set(null);
            warningMessage.set("Select up to " + getSelectionLimit() + " mods.");
            return false;
        }
        internalChange = true;
        option.setSelected(selected);
        internalChange = false;
        if (selected) {
            if (!selectedBacking.contains(option.mod())) {
                selectedBacking.add(option.mod());
            }
            warningMessage.set(null);
        } else {
            selectedBacking.remove(option.mod());
            warningMessage.set(null);
        }
        updateSummary();
        return true;
    }

    public void clearSelection() {
        if (options.isEmpty() && selectedBacking.isEmpty()) {
            return;
        }
        internalChange = true;
        for (ModOption option : options) {
            option.setSelected(false);
        }
        internalChange = false;
        selectedBacking.clear();
        warningMessage.set(null);
        updateSummary();
    }

    private void onOptionSelectionChanged(ModOption option, boolean selected) {
        if (internalChange) {
            return;
        }
        if (selected) {
            if (selectedBacking.contains(option.mod())) {
                return;
            }
            if (selectedBacking.size() >= getSelectionLimit()) {
                internalChange = true;
                option.setSelected(false);
                internalChange = false;
                warningMessage.set(null);
                warningMessage.set("Select up to " + getSelectionLimit() + " mods.");
                return;
            }
            selectedBacking.add(option.mod());
            warningMessage.set(null);
        } else {
            if (selectedBacking.remove(option.mod())) {
                warningMessage.set(null);
            }
        }
        updateSummary();
    }

    private void trimSelectionIfNeeded() {
        if (selectedBacking.size() <= getSelectionLimit()) {
            return;
        }
        List<RegisteredMod> retained = new ArrayList<>(selectedBacking.subList(0, getSelectionLimit()));
        Set<String> retainedKeys = new HashSet<>();
        retained.forEach(mod -> retainedKeys.add(normalizeKey(mod.modId())));

        internalChange = true;
        for (ModOption option : options) {
            if (!retainedKeys.contains(normalizeKey(option.mod().modId()))) {
                option.setSelected(false);
            }
        }
        internalChange = false;

        selectedBacking.setAll(retained);
        warningMessage.set(null);
        warningMessage.set("Select up to " + getSelectionLimit() + " mods.");
        updateSummary();
    }

    private void updateSummary() {
        if (selectedBacking.isEmpty()) {
            summary.set("Mods (0)");
            return;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Mods (").append(selectedBacking.size()).append(") – ");
        int previewLimit = 3;
        for (int i = 0; i < selectedBacking.size(); i++) {
            if (i > 0 && i < previewLimit) {
                builder.append(", ");
            }
            if (i >= previewLimit) {
                builder.append("…");
                break;
            }
            builder.append(selectedBacking.get(i).displayName());
        }
        summary.set(builder.toString());
    }

    private static String normalizeKey(String modId) {
        return modId == null ? "" : modId.trim().toLowerCase(Locale.ROOT);
    }

    public static final class ModOption {
        private final RegisteredMod mod;
        private final BooleanProperty selected = new SimpleBooleanProperty(false);

        private ModOption(RegisteredMod mod) {
            this.mod = Objects.requireNonNull(mod, "mod");
        }

        public RegisteredMod mod() {
            return mod;
        }

        public boolean isSelected() {
            return selected.get();
        }

        private void setSelected(boolean value) {
            selected.set(value);
        }

        public BooleanProperty selectedProperty() {
            return selected;
        }
    }
}
