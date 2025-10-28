package dev.ftbq.editor.ui.model;

import dev.ftbq.editor.domain.LootTable;
import dev.ftbq.editor.services.generator.RewardConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardSelectionModelTest {

    @Test
    void togglesLootTableSelection() {
        RewardSelectionModel model = new RewardSelectionModel();
        model.setAvailableLootTables(List.of(
                LootTable.builder().id("core/common").build(),
                LootTable.builder().id("core/rare").build()));

        assertTrue(model.summaryProperty().get().contains("[0/2]"));

        model.toggleLootTable("core/common");
        assertTrue(model.selectedLootTables().contains("core/common"));
        assertTrue(model.summaryProperty().get().contains("[1/2]"));

        model.toggleLootTable("core/common");
        assertFalse(model.selectedLootTables().contains("core/common"));
    }

    @Test
    void toConfigurationReflectsToggles() {
        RewardSelectionModel model = new RewardSelectionModel();
        model.setAvailableLootTables(List.of(LootTable.builder().id("loot/a").build()));
        model.itemRewardsEnabledProperty().set(true);
        model.xpRewardsEnabledProperty().set(false);
        model.lootTableRewardsEnabledProperty().set(true);
        model.toggleLootTable("loot/a");

        RewardConfiguration configuration = model.toConfiguration();
        assertTrue(configuration.allowItemRewards());
        assertFalse(configuration.allowXpRewards());
        assertTrue(configuration.allowLootTableRewards());
        assertEquals(List.of("loot/a"), configuration.preferredLootTables());
    }
}
