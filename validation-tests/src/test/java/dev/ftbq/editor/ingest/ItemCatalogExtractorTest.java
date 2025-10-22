package dev.ftbq.editor.ingest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemCatalogExtractorTest {

    @Test
    void extractsMinecraftNamespaceWhenJarAvailable() throws Exception {
        Path artifactsDir = Paths.get("artifacts");
        Assumptions.assumeTrue(Files.exists(artifactsDir), "No artifacts directory present");

        Optional<Path> jarPath;
        try (Stream<Path> stream = Files.walk(artifactsDir)) {
            jarPath = stream
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".jar"))
                    .min(Comparator.naturalOrder());
        }

        Assumptions.assumeTrue(jarPath.isPresent(), "No JAR files available in artifacts");

        Path jar = jarPath.get();
        boolean isVanilla = jar.getFileName().toString().toLowerCase(Locale.ROOT).contains("minecraft");

        ItemCatalog catalog = ItemCatalogExtractor.extract(jar, jar.getFileName().toString(), "test", isVanilla);

        long minecraftCount = catalog.items().stream()
                .filter(meta -> "minecraft".equals(meta.namespace()))
                .count();

        assertTrue(minecraftCount > 0, "Expected minecraft namespace entries in catalog");
    }
}
