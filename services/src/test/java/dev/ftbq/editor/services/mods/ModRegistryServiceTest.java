package dev.ftbq.editor.services.mods;

import dev.ftbq.editor.ingest.ItemCatalog;
import dev.ftbq.editor.ingest.ItemMeta;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModRegistryServiceTest {

    @Test
    void registersModsFromCatalog() {
        ItemCatalog catalog = new ItemCatalog(
                "example.jar",
                "1.20.1",
                false,
                List.of(
                        new ItemMeta("example:widget", "Widget", "example", "item", false, null, null, "example", "Example Mod", "2.0"),
                        new ItemMeta("example:gadget", "Gadget", "example", "item", false, null, null, "example", "Example Mod", "2.0"),
                        new ItemMeta("another:thing", "Thing", "another", "item", false, null, null, "another", "Another Mod", "1.5"),
                        new ItemMeta("minecraft:stone", "Stone", "minecraft", "item", true, null, null, null, null, null)
                ),
                Map.of()
        );

        ModRegistryService service = new ModRegistryService();
        List<RegisteredMod> mods = service.register(catalog);

        assertEquals(2, mods.size());
        RegisteredMod example = mods.stream()
                .filter(mod -> mod.modId().equals("example"))
                .findFirst()
                .orElseThrow();
        assertEquals("Example Mod", example.displayName());
        assertEquals(List.of("example:gadget", "example:widget"), example.itemIds());
        assertEquals("2.0", example.version());
        assertEquals("example.jar", example.sourceJar());

        RegisteredMod another = mods.stream()
                .filter(mod -> mod.modId().equals("another"))
                .findFirst()
                .orElseThrow();
        assertEquals("Another Mod", another.displayName());
        assertEquals(List.of("another:thing"), another.itemIds());
        assertEquals("1.5", another.version());
    }

    @Test
    void notifiesListenersOnChange() {
        ItemCatalog catalog = new ItemCatalog(
                "example.jar",
                "1.0",
                false,
                List.of(new ItemMeta("example:item", "Item", "example", "item", false, null, null, "example", "Example", "1.0")),
                Map.of()
        );

        ModRegistryService service = new ModRegistryService();
        AtomicInteger notifications = new AtomicInteger();
        service.addListener(snapshot -> notifications.incrementAndGet());

        service.register(catalog);
        assertTrue(notifications.get() >= 1, "Expected listener to be notified on registration");

        service.clear();
        assertTrue(notifications.get() >= 2, "Expected listener to be notified on clear");
    }
}
