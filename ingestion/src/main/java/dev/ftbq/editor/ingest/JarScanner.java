package dev.ftbq.editor.ingest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for reading the contents of Minecraft related JAR files without loading classes.
 * <p>
 * Both vanilla and mod JARs are treated as simple ZIP archives where each entry is captured as
 * {@link JarEntryInfo}. The scanner never executes code or triggers class loading – it only
 * collects metadata that can be used by higher level ingestion workflows.
 */
public final class JarScanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(JarScanner.class);

    private JarScanner() {
        throw new AssertionError("JarScanner cannot be instantiated");
    }

    /**
     * Scan a vanilla Minecraft JAR.
     *
     * @param jar     path to the vanilla JAR file
     * @param version vanilla version identifier
     * @return immutable scan result containing the entries of the JAR
     * @throws IOException if the archive cannot be read
     */
    public static JarScanResult scanVanillaJar(Path jar, String version) throws IOException {
        return scanJar(jar, "vanilla", Objects.requireNonNull(version, "version"));
    }

    /**
     * Scan a mod JAR. The version hint is simply propagated in the result for later evaluation.
     *
     * @param jar         path to the mod JAR file
     * @param versionHint optional version hint extracted from metadata outside the file
     * @return immutable scan result containing the entries of the JAR
     * @throws IOException if the archive cannot be read
     */
    public static JarScanResult scanModJar(Path jar, String versionHint) throws IOException {
        return scanJar(jar, "mod", versionHint);
    }

    private static JarScanResult scanJar(Path jar, String kind, String versionLabel) throws IOException {
        Objects.requireNonNull(jar, "jar");
        Objects.requireNonNull(kind, "kind");

        LOGGER.info("Scanning jar | path={} kind={} version={}", jar, kind, versionLabel);
        List<JarEntryInfo> entries = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
            while (enumeration.hasMoreElements()) {
                ZipEntry entry = enumeration.nextElement();
                entries.add(new JarEntryInfo(
                        entry.getName(),
                        entry.isDirectory(),
                        entry.getSize(),
                        entry.getCompressedSize()
                ));
            }
        }

        JarScanResult result = new JarScanResult(kind, versionLabel, Collections.unmodifiableList(entries));
        LOGGER.info("Jar scan complete | path={} kind={} version={} entries={}", jar, kind, versionLabel, entries.size());
        return result;
    }

    /**
     * Summary metadata for a single entry inside a scanned JAR.
     *
     * @param name            full path name of the entry
     * @param directory       whether the entry represents a directory
     * @param size            uncompressed size in bytes or {@code -1} if not known
     * @param compressedSize  compressed size in bytes or {@code -1} if not known
     */
    public record JarEntryInfo(String name, boolean directory, long size, long compressedSize) {
        public JarEntryInfo {
            Objects.requireNonNull(name, "name");
        }
    }

    /**
     * Result object returned by scanning operations.
     *
     * @param kind         indicates whether the source JAR was vanilla or modded
     * @param versionLabel vanilla version or mod version hint as supplied to the scanner
     * @param entries      immutable list of entries contained in the JAR
     */
    public record JarScanResult(String kind, String versionLabel, List<JarEntryInfo> entries) {
        public JarScanResult {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(entries, "entries");
        }
    }
}
