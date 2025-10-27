package dev.ftbq.editor.assets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CacheManagerTest {

    @TempDir
    Path tempDir;

    @Test
    void storeAndFetchIcon() {
        CacheManager cacheManager = new CacheManager(tempDir.resolve(".cache"));
        byte[] iconData = new byte[] {1, 2, 3};

        String hash = cacheManager.storeIcon(iconData);
        Optional<byte[]> restored = cacheManager.fetchIcon(hash);

        assertTrue(restored.isPresent());
        assertArrayEquals(iconData, restored.get());
    }

    @Test
    void storeAndFetchBackground() {
        CacheManager cacheManager = new CacheManager(tempDir.resolve(".cache"));
        byte[] background = new byte[] {4, 5, 6, 7};

        String hash = cacheManager.storeBackground(background);
        Optional<byte[]> restored = cacheManager.fetchBackground(hash);

        assertTrue(restored.isPresent());
        assertArrayEquals(background, restored.get());
    }

    @Test
    void iconCacheEvictsLeastRecentlyUsedEntry() {
        CacheManager cacheManager = new CacheManager(tempDir.resolve(".cache"), 2, 2);
        String first = cacheManager.storeIcon(new byte[] {10});
        String second = cacheManager.storeIcon(new byte[] {11});
        cacheManager.fetchIcon(first);
        String third = cacheManager.storeIcon(new byte[] {12});

        assertTrue(cacheManager.fetchIcon(first).isPresent(), "first should be retained");
        assertTrue(cacheManager.fetchIcon(third).isPresent(), "third should be available");
        assertTrue(cacheManager.fetchIcon(second).isEmpty(), "second should be evicted");
    }

    @Test
    void backgroundCacheEvictsLeastRecentlyUsedEntry() {
        CacheManager cacheManager = new CacheManager(tempDir.resolve(".cache"), 2, 2);
        String first = cacheManager.storeBackground(new byte[] {20});
        String second = cacheManager.storeBackground(new byte[] {21});
        cacheManager.fetchBackground(first);
        String third = cacheManager.storeBackground(new byte[] {22});

        assertTrue(cacheManager.fetchBackground(first).isPresent(), "first should be retained");
        assertTrue(cacheManager.fetchBackground(third).isPresent(), "third should be available");
        assertTrue(cacheManager.fetchBackground(second).isEmpty(), "second should be evicted");
    }

    @Test
    void storingSameBytesReturnsStableHash() {
        CacheManager cacheManager = new CacheManager(tempDir.resolve(".cache"));
        byte[] icon = new byte[] {30, 31};

        String firstHash = cacheManager.storeIcon(icon);
        String secondHash = cacheManager.storeIcon(icon);

        assertEquals(firstHash, secondHash);
    }

    @Test
    void fetchesFallbackIconForNamespacedResource() {
        CacheManager cacheManager = new CacheManager(tempDir.resolve(".cache"));

        Optional<byte[]> fallback = cacheManager.fetchIcon("minecraft:apple");

        assertTrue(fallback.isPresent(), "Expected fallback icon for namespaced resource");
        assertTrue(fallback.get().length > 0, "Fallback icon should contain data");
    }
}
