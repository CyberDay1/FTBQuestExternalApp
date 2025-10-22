package dev.ftbq.editor.services.version;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.version.ItemCatalog;
import dev.ftbq.editor.domain.version.MinecraftVersion;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VersionCatalogImplTest {

    @Test
    void cachesMergedSnapshotsByVersionAndInvalidatesOnToggle() {
        ItemCatalog vanillaOne = new TestCatalog("vanilla_one:item");
        ItemCatalog vanillaTwo = new TestCatalog("vanilla_two:item");
        VersionCatalogImpl catalog = new VersionCatalogImpl(
                Map.of(MinecraftVersion.V1_20_1, vanillaOne, MinecraftVersion.V1_20_2, vanillaTwo),
                MinecraftVersion.V1_20_1);

        ItemCatalog modA = new TestCatalog("mod_a:item");
        ItemCatalog firstMerge = catalog.mergeWithMods(List.of(modA));
        ItemCatalog secondMerge = catalog.mergeWithMods(List.of(modA));
        assertSame(firstMerge, secondMerge, "Expected cached snapshot to be reused for the same version");

        catalog.setActiveVersion(MinecraftVersion.V1_20_2);
        ItemCatalog modB = new TestCatalog("mod_b:item");
        ItemCatalog versionTwoMerge = catalog.mergeWithMods(List.of(modB));
        ItemCatalog versionTwoMergeAgain = catalog.mergeWithMods(List.of(modB));
        assertSame(versionTwoMerge, versionTwoMergeAgain, "Expected snapshot cache for second version to be reused");

        catalog.setActiveVersion(MinecraftVersion.V1_20_1);
        ItemCatalog afterToggle = catalog.mergeWithMods(List.of(modA));
        assertNotSame(
                firstMerge,
                afterToggle,
                "Switching versions should invalidate previous snapshot and rebuild it on next merge");
    }

    @Test
    void explicitInvalidationClearsCachedSnapshot() {
        VersionCatalogImpl catalog = new VersionCatalogImpl(
                Map.of(MinecraftVersion.V1_20_1, new TestCatalog("vanilla:item")),
                MinecraftVersion.V1_20_1);

        ItemCatalog modCatalog = new TestCatalog("mod:item");
        ItemCatalog merged = catalog.mergeWithMods(List.of(modCatalog));

        catalog.invalidateSnapshots();

        ItemCatalog rebuilt = catalog.mergeWithMods(List.of(modCatalog));
        assertNotSame(merged, rebuilt, "Invalidating snapshots should force cache rebuild");
    }

    private static final class TestCatalog implements ItemCatalog {
        private final List<ItemRef> items;

        TestCatalog(String id) {
            this.items = List.of(new ItemRef(id, 1));
        }

        @Override
        public Collection<ItemRef> items() {
            return items;
        }
    }
}

