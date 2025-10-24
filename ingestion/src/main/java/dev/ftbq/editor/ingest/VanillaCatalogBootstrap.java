package dev.ftbq.editor.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Command line utility that builds vanilla item catalogs from Minecraft JAR files.
 */
public final class VanillaCatalogBootstrap {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final Logger LOGGER = LoggerFactory.getLogger(VanillaCatalogBootstrap.class);

    private VanillaCatalogBootstrap() {
        throw new AssertionError("VanillaCatalogBootstrap cannot be instantiated");
    }

    public static void main(String[] args) throws IOException {
        Path root = Path.of("").toAbsolutePath();
        Path artifactsDir = root.resolve("artifacts").resolve("catalogs");
        Path inputDir = artifactsDir.resolve("input-jars");

        if (!Files.isDirectory(inputDir)) {
            throw new IllegalStateException("Missing input directory: " + inputDir);
        }

        LOGGER.info("Starting vanilla catalog bootstrap | inputDir={}", inputDir);
        List<Path> jars;
        try (Stream<Path> stream = Files.list(inputDir)) {
            jars = stream
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        if (jars.isEmpty()) {
            throw new IllegalStateException("No vanilla JARs found in " + inputDir);
        }

        Files.createDirectories(artifactsDir);

        boolean anyFailure = false;
        for (Path jar : jars) {
            String fileName = jar.getFileName().toString();
            String version = stripJarExtension(fileName);
            if (version.isEmpty()) {
                throw new IllegalStateException("Unable to determine version from file name: " + fileName);
            }

            JarScanner.JarScanResult scanResult = JarScanner.scanVanillaJar(jar, version);
            ItemCatalog catalog = ItemCatalogExtractor.extract(jar, fileName, version, true);

            int itemCount = catalog.items().size();
            LOGGER.info("Vanilla catalog extracted | version={} items={} jar={}"
                    , version, itemCount, jar);

            if (itemCount < 1000) {
                anyFailure = true;
            }

            Path output = artifactsDir.resolve("vanilla_" + version + ".json");
            try (OutputStream outputStream = Files.newOutputStream(output)) {
                MAPPER.writeValue(outputStream, catalog);
            }

            // Simple sanity log for the scan as additional context.
            LOGGER.info("Jar scan summary | version={} entries={}", version, scanResult.entries().size());
        }

        if (anyFailure) {
            throw new IllegalStateException("One or more vanilla catalogs contained fewer than 1000 items");
        }

        LOGGER.info("Vanilla catalog bootstrap complete | processedJars={}", jars.size());
    }

    private static String stripJarExtension(String fileName) {
        Objects.requireNonNull(fileName, "fileName");
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return "";
        }
        return fileName.substring(0, dot);
    }
}
