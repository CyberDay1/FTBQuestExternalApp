package dev.ftbq.editor.ingest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ItemCatalogExtractorIconTest {

    @Test
    void cachesIconWhenModelPresent(@TempDir Path tempDir) throws Exception {
        Path jarPath = tempDir.resolve("sample.jar");
        byte[] texture = createTexture();

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jarPath))) {
            writeEntry(zip, "assets/test/lang/en_us.json", "{\"item.test.example\":\"Example Item\"}");
            writeEntry(zip, "assets/test/models/item/example.json",
                    "{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"item/example\"}}");
            writeEntry(zip, "assets/test/textures/item/example.png", texture);
        }

        clearCache();
        try {
            ItemCatalog catalog = ItemCatalogExtractor.extract(jarPath, "sample", "1.0.0", false);

            ItemMeta meta = catalog.items().stream()
                    .filter(item -> item.id().equals("test:example"))
                    .findFirst()
                    .orElseThrow();

            assertEquals("test:item/example", meta.texturePath());
            assertNotNull(meta.iconHash());
            assertFalse(meta.iconHash().isBlank());
            assertEquals("test:example", meta.iconHash());

            Path cachedIcon = Path.of(".cache", "icons", "test", "example.png");
            assertTrue(Files.exists(cachedIcon), "Icon cache file should exist");
        } finally {
            clearCache();
        }
    }

    @Test
    void mergesWithExistingCatalog(@TempDir Path tempDir) throws Exception {
        Path jarPath = tempDir.resolve("merge.jar");
        byte[] texture = createTexture();

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jarPath))) {
            writeEntry(zip, "assets/test/lang/en_us.json", "{\"item.test.example\":\"Example Item\"}");
            writeEntry(zip, "assets/test/models/item/example.json",
                    "{\"parent\":\"item/generated\",\"textures\":{\"layer0\":\"item/example\"}}");
            writeEntry(zip, "assets/test/textures/item/example.png", texture);
            writeEntry(zip, "data/test/tags/items/tools.json",
                    "{\"replace\":false,\"values\":[\"test:example\"]}");
        }

        ItemMeta existingItem = new ItemMeta(
                "base:item",
                "Base Item",
                "base",
                "item",
                false,
                null,
                null,
                "base",
                "Base Mod",
                "1.0.0");

        ItemCatalog existingCatalog = new ItemCatalog(
                "existing.jar",
                "1.0.0",
                false,
                List.of(existingItem),
                Map.of("forge:existing", List.of("base:item")));

        clearCache();
        try {
            ItemCatalog merged = ItemCatalogExtractor.extract(jarPath, "merge", "1.0.0", false, existingCatalog);

            assertTrue(merged.items().stream().anyMatch(meta -> meta.id().equals("base:item")),
                    "Existing catalog items should be retained");
            assertTrue(merged.items().stream().anyMatch(meta -> meta.id().equals("test:example")),
                    "Newly extracted items should be added");

            Map<String, List<String>> tags = merged.tags();
            assertTrue(tags.containsKey("forge:existing"));
            assertTrue(tags.get("forge:existing").contains("base:item"));
            assertTrue(tags.containsKey("test:tools"));
            assertTrue(tags.get("test:tools").contains("test:example"));
        } finally {
            clearCache();
        }
    }

    private static void writeEntry(ZipOutputStream zip, String name, String content) {
        writeEntry(zip, name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeEntry(ZipOutputStream zip, String name, byte[] data) {
        try {
            ZipEntry entry = new ZipEntry(name);
            zip.putNextEntry(entry);
            zip.write(data);
            zip.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] createTexture() throws IOException {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(255, 0, 0, 255));
        graphics.fillRect(0, 0, 16, 16);
        graphics.dispose();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "PNG", output);
            return output.toByteArray();
        }
    }

    private static void clearCache() throws IOException {
        Path cacheRoot = Path.of(".cache");
        if (!Files.exists(cacheRoot)) {
            return;
        }
        try (var paths = Files.walk(cacheRoot)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
    }
}

