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
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Command line utility that builds vanilla item catalogs from Minecraft JAR files.
 */
public final class VanillaCatalogBootstrap {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

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
            System.out.printf(Locale.ROOT, "Vanilla %s: %d items%n", version, itemCount);

            if (itemCount < 1000) {
                anyFailure = true;
            }

            Path output = artifactsDir.resolve("vanilla_" + version + ".json");
            try (OutputStream outputStream = Files.newOutputStream(output)) {
                MAPPER.writeValue(outputStream, catalog);
            }

            // Simple sanity log for the scan as additional context.
            System.out.printf(Locale.ROOT, "  -> %d jar entries scanned%n", scanResult.entries().size());
        }

        if (anyFailure) {
            throw new IllegalStateException("One or more vanilla catalogs contained fewer than 1000 items");
        }
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
