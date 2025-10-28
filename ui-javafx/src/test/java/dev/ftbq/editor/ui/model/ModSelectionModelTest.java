package dev.ftbq.editor.ui.model;

import dev.ftbq.editor.services.mods.RegisteredMod;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModSelectionModelTest {

    @Test
    void enforcesSelectionLimitAndUpdatesSummary() {
        ModSelectionModel model = new ModSelectionModel();
        model.setSelectionLimit(2);
        List<RegisteredMod> mods = List.of(
                new RegisteredMod("alpha", "Alpha", "1.0", List.of("alpha:item"), "alpha.jar"),
                new RegisteredMod("beta", "Beta", "1.0", List.of("beta:item"), "beta.jar"),
                new RegisteredMod("gamma", "Gamma", "1.0", List.of("gamma:item"), "gamma.jar")
        );

        model.setAvailableMods(mods);
        ModSelectionModel.ModOption first = model.options().get(0);
        ModSelectionModel.ModOption second = model.options().get(1);
        ModSelectionModel.ModOption third = model.options().get(2);

        assertTrue(model.setOptionSelected(first, true));
        assertTrue(model.setOptionSelected(second, true));
        assertEquals(2, model.selectedModsSnapshot().size());
        assertTrue(model.summaryProperty().get().startsWith("Mods (2)"));

        assertFalse(model.setOptionSelected(third, true), "Selection should be blocked when exceeding limit");
        assertEquals(2, model.selectedModsSnapshot().size());

        model.clearSelection();
        assertEquals(0, model.selectedModsSnapshot().size());
        assertEquals("Mods (0)", model.summaryProperty().get());
    }
}
