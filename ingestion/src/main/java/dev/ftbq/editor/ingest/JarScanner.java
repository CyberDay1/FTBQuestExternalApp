package dev.ftbq.editor.ingest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FilenameUtils;
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

    private static final Pattern MOD_ASSET_PATTERN =
            Pattern.compile("assets/([^/]+)/(?:(?:models|textures)/item)/([^/]+)\\.[a-zA-Z0-9]+");

    private static final Pattern ENTITY_TEXTURE_PATTERN =
            Pattern.compile("assets/([^/]+)/textures/entity/([^/]+?)(?:/[^/]+)?\\.[a-zA-Z0-9]+");

    private static final Pattern ENTITY_MODEL_PATTERN =
            Pattern.compile("assets/([^/]+)/(?:geo|models/entity)/([^/]+?)(?:\\.geo)?\\.json");

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

    /**
     * Extract proxy items from models and textures when formal JSON definitions are missing.
     */
    public static List<ItemMeta> extractProxyItems(Path jar, String version) throws IOException {
        Objects.requireNonNull(jar, "jar");

        Map<String, ProxyItemDescriptor> descriptors = new LinkedHashMap<>();
        var resolvedFile = jar.toAbsolutePath().toFile();
        if (!resolvedFile.exists()) {
            LOGGER.warn("Jar file not found for proxy scan: {}", resolvedFile);
            return List.of();
        }
        try (ZipFile zipFile = new ZipFile(resolvedFile)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String path = entry.getName();
                Matcher matcher = MOD_ASSET_PATTERN.matcher(path);
                if (!matcher.find()) {
                    continue;
                }

                String modId = matcher.group(1);
                String fileName = matcher.group(2);
                String itemName = FilenameUtils.getBaseName(fileName);
                if (itemName == null || itemName.isBlank()) {
                    continue;
                }

                String id = modId + ":" + itemName;
                ProxyItemDescriptor descriptor = descriptors.computeIfAbsent(id, ignored ->
                        new ProxyItemDescriptor(id, toDisplayName(itemName), modId, resolveModName(jar, modId)));

                if (descriptor.texturePath == null || path.contains("/textures/item/")) {
                    descriptor.texturePath = path;
                }
            }
        }

        List<String> ids = new ArrayList<>(descriptors.keySet());
        Collections.sort(ids);

        List<ItemMeta> proxyItems = new ArrayList<>(ids.size());
        for (String id : ids) {
            ProxyItemDescriptor descriptor = descriptors.get(id);
            proxyItems.add(new ItemMeta(
                    descriptor.id,
                    descriptor.displayName,
                    descriptor.modId,
                    "proxy_item",
                    false,
                    descriptor.texturePath,
                    null,
                    descriptor.modId,
                    descriptor.modName,
                    version
            ));
        }

        LOGGER.info("Proxy item scan complete | path={} items={}", jar, proxyItems.size());
        return proxyItems;
    }

    public static List<EntityMeta> extractProxyEntities(Path jar, String version) throws IOException {
        Objects.requireNonNull(jar, "jar");

        Map<String, ProxyEntityDescriptor> descriptors = new LinkedHashMap<>();
        var resolvedFile = jar.toAbsolutePath().toFile();
        if (!resolvedFile.exists()) {
            LOGGER.warn("Jar file not found for entity scan: {}", resolvedFile);
            return List.of();
        }

        boolean isVanilla = isVanillaJar(jar);

        try (ZipFile zipFile = new ZipFile(resolvedFile)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String path = entry.getName();

                Matcher textureMatcher = ENTITY_TEXTURE_PATTERN.matcher(path);
                if (textureMatcher.find()) {
                    String modId = textureMatcher.group(1);
                    String entityName = textureMatcher.group(2);
                    if (entityName != null && !entityName.isBlank()) {
                        String id = modId + ":" + entityName;
                        ProxyEntityDescriptor descriptor = descriptors.computeIfAbsent(id, ignored ->
                                new ProxyEntityDescriptor(id, toDisplayName(entityName), modId, resolveModName(jar, modId)));
                        if (descriptor.texturePath == null) {
                            descriptor.texturePath = path;
                        }
                    }
                    continue;
                }

                Matcher modelMatcher = ENTITY_MODEL_PATTERN.matcher(path);
                if (modelMatcher.find()) {
                    String modId = modelMatcher.group(1);
                    String entityName = FilenameUtils.getBaseName(modelMatcher.group(2));
                    if (entityName != null && !entityName.isBlank()) {
                        String id = modId + ":" + entityName;
                        descriptors.computeIfAbsent(id, ignored ->
                                new ProxyEntityDescriptor(id, toDisplayName(entityName), modId, resolveModName(jar, modId)));
                    }
                }
            }
        }

        List<String> ids = new ArrayList<>(descriptors.keySet());
        Collections.sort(ids);

        List<EntityMeta> proxyEntities = new ArrayList<>(ids.size());
        for (String id : ids) {
            ProxyEntityDescriptor descriptor = descriptors.get(id);
            proxyEntities.add(new EntityMeta(
                    descriptor.id,
                    descriptor.displayName,
                    descriptor.modId,
                    isVanilla || "minecraft".equalsIgnoreCase(descriptor.modId),
                    descriptor.texturePath,
                    descriptor.modId,
                    descriptor.modName,
                    version
            ));
        }

        LOGGER.info("Proxy entity scan complete | path={} entities={}", jar, proxyEntities.size());
        return proxyEntities;
    }

    private static boolean isVanillaJar(Path jar) {
        String fileName = jar.getFileName() != null ? jar.getFileName().toString().toLowerCase() : "";
        return fileName.contains("minecraft") && !fileName.contains("forge") && !fileName.contains("fabric");
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

    private static String resolveModName(Path jar, String modId) {
        Path fileName = jar.getFileName();
        if (fileName != null) {
            String baseName = FilenameUtils.getBaseName(fileName.toString());
            if (!baseName.isBlank()) {
                return baseName;
            }
        }
        return modId;
    }

    private static String toDisplayName(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return "Item";
        }
        String[] parts = itemName.split("[_-]");
        StringBuilder builder = new StringBuilder(itemName.length());
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                builder.append(part.substring(1));
            }
        }
        return builder.length() > 0 ? builder.toString() : itemName;
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

    private static final class ProxyItemDescriptor {
        private final String id;
        private final String displayName;
        private final String modId;
        private final String modName;
        private String texturePath;

        private ProxyItemDescriptor(String id, String displayName, String modId, String modName) {
            this.id = id;
            this.displayName = displayName;
            this.modId = modId;
            this.modName = modName;
        }
    }

    private static final class ProxyEntityDescriptor {
        private final String id;
        private final String displayName;
        private final String modId;
        private final String modName;
        private String texturePath;

        private ProxyEntityDescriptor(String id, String displayName, String modId, String modName) {
            this.id = id;
            this.displayName = displayName;
            this.modId = modId;
            this.modName = modName;
        }
    }
}
