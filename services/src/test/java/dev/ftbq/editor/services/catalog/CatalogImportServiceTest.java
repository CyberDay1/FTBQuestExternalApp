package dev.ftbq.editor.services.catalog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.ftbq.editor.ingest.ItemCatalog;
import dev.ftbq.editor.ingest.ItemMeta;
import dev.ftbq.editor.store.Jdbc;
import dev.ftbq.editor.store.StoreDao;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogImportServiceTest {

    @Test
    void importsCatalogIncludingModAndIconMetadata() throws Exception {
        try (Connection connection = Jdbc.openInMemory()) {
            StoreDao dao = new StoreDao(connection);
            CatalogImportService importer = new CatalogImportService(dao);

            ItemMeta vanillaStone = new ItemMeta(
                    "minecraft:stone",
                    "Stone",
                    "minecraft",
                    "block",
                    true,
                    "minecraft:block/stone",
                    "vanilla-hash",
                    "minecraft",
                    "Minecraft",
                    "1.20.1");

            ItemCatalog vanillaCatalog = new ItemCatalog(
                    "minecraft.jar",
                    "1.20.1",
                    true,
                    List.of(vanillaStone),
                    Map.of("minecraft:mineable/pickaxe", List.of("minecraft:stone")));
            importer.importCatalog(vanillaCatalog);

            ItemMeta alphaSword = new ItemMeta(
                    "alpha:sword",
                    "Alpha Sword",
                    "alpha",
                    "item",
                    false,
                    "alpha:item/sword",
                    "alpha-hash",
                    "alpha",
                    "Alpha Tools",
                    "2.0.0");

            ItemCatalog alphaCatalog = new ItemCatalog(
                    "alpha.jar",
                    "1.20.1",
                    false,
                    List.of(alphaSword),
                    Map.of("forge:swords", List.of("alpha:sword")));
            importer.importCatalog(alphaCatalog);

            ItemMeta betaWand = new ItemMeta(
                    "beta:wand",
                    "Beta Wand",
                    "beta",
                    "item",
                    false,
                    "beta:item/wand",
                    "beta-hash",
                    "beta",
                    "Beta Magic",
                    "3.1.0");

            ItemCatalog betaCatalog = new ItemCatalog(
                    "beta.jar",
                    "1.20.1",
                    false,
                    List.of(betaWand),
                    Map.of("forge:wands", List.of("beta:wand")));
            importer.importCatalog(betaCatalog);

            StoreDao.ItemEntity alphaEntity = dao.findItemById("alpha:sword").orElseThrow();
            assertEquals("alpha", alphaEntity.modId());
            assertEquals("Alpha Tools", alphaEntity.modName());
            assertEquals("alpha:item/sword", alphaEntity.texturePath());
            assertEquals("alpha-hash", alphaEntity.iconHash());
            assertEquals("[\"forge:swords\"]", alphaEntity.tags());
            assertEquals("alpha.jar", alphaEntity.sourceJar());
            assertEquals("1.20.1", alphaEntity.version());
            assertEquals("item", alphaEntity.kind());

            StoreDao.ItemEntity vanillaEntity = dao.findItemById("minecraft:stone").orElseThrow();
            assertEquals("minecraft", vanillaEntity.modId());
            assertEquals("Minecraft", vanillaEntity.modName());
            assertEquals("[\"minecraft:mineable/pickaxe\"]", vanillaEntity.tags());
            assertEquals("minecraft:block/stone", vanillaEntity.texturePath());
            assertEquals("vanilla-hash", vanillaEntity.iconHash());

            List<StoreDao.ItemEntity> alphaFiltered = dao.listItems(
                    null,
                    List.of(),
                    "alpha tools",
                    null,
                    null,
                    StoreDao.SortMode.NAME,
                    Integer.MAX_VALUE,
                    0);
            assertEquals(1, alphaFiltered.size());
            assertEquals("alpha:sword", alphaFiltered.get(0).id());

            List<StoreDao.ItemEntity> sortedByMod = dao.listItems(
                    null,
                    List.of(),
                    null,
                    null,
                    null,
                    StoreDao.SortMode.MOD,
                    Integer.MAX_VALUE,
                    0);
            assertEquals(List.of("alpha:sword", "beta:wand", "minecraft:stone"),
                    sortedByMod.stream().map(StoreDao.ItemEntity::id).toList());

            assertTrue(sortedByMod.get(0).modName().compareToIgnoreCase(sortedByMod.get(1).modName()) <= 0);
            assertNotNull(sortedByMod.get(2).modId());
        }
    }
}
