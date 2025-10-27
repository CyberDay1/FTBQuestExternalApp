package dev.ftbq.editor.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Utility methods for resolving the primary mod identifier from a mod JAR.
 */
public final class ModIdResolver {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ModIdResolver() {
        throw new AssertionError("ModIdResolver cannot be instantiated");
    }

    public static Optional<String> resolveModId(File jar) {
        if (jar == null || !jar.isFile()) {
            return Optional.empty();
        }

        try (ZipFile zipFile = new ZipFile(jar)) {
            Optional<String> fabricId = readModIdFromJson(zipFile, "fabric.mod.json");
            if (fabricId.isPresent()) {
                return fabricId;
            }

            Optional<String> quiltId = readModIdFromJson(zipFile, "quilt.mod.json");
            if (quiltId.isPresent()) {
                return quiltId;
            }

            Optional<String> tomlId = readModIdFromToml(zipFile, "META-INF/mods.toml");
            if (tomlId.isPresent()) {
                return tomlId;
            }
        } catch (IOException ignored) {
            // Ignore unreadable JARs and fall back to an empty result.
        }

        return Optional.empty();
    }

    private static Optional<String> readModIdFromJson(ZipFile zipFile, String entryName) throws IOException {
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) {
            return Optional.empty();
        }
        try (InputStream input = zipFile.getInputStream(entry)) {
            JsonNode root = MAPPER.readTree(input);
            if (root == null || root.isMissingNode()) {
                return Optional.empty();
            }
            JsonNode idNode = root.get("id");
            if (idNode == null || !idNode.isTextual()) {
                idNode = root.get("modid");
            }
            if (idNode != null && idNode.isTextual()) {
                String value = idNode.asText().trim();
                if (!value.isEmpty()) {
                    return Optional.of(value);
                }
            }
            return Optional.empty();
        }
    }

    private static Optional<String> readModIdFromToml(ZipFile zipFile, String entryName) throws IOException {
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) {
            return Optional.empty();
        }
        try (InputStream input = zipFile.getInputStream(entry);
                InputStreamReader streamReader = new InputStreamReader(input, StandardCharsets.UTF_8);
                BufferedReader reader = new BufferedReader(streamReader)) {
            boolean inModsSection = false;
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = trimComment(line);
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("[[")) {
                    inModsSection = trimmed.equalsIgnoreCase("[[mods]]");
                    continue;
                }
                if (!inModsSection) {
                    continue;
                }
                if (startsWithIgnoreCase(trimmed, "modId") || startsWithIgnoreCase(trimmed, "modid")) {
                    int equals = trimmed.indexOf('=');
                    if (equals < 0) {
                        continue;
                    }
                    String rawValue = trimmed.substring(equals + 1).trim();
                    String cleaned = stripQuotes(trimComment(rawValue));
                    if (!cleaned.isEmpty()) {
                        return Optional.of(cleaned);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        int length = prefix.length();
        if (value.length() < length) {
            return false;
        }
        return value.regionMatches(true, 0, prefix, 0, length);
    }

    private static String trimComment(String value) {
        String trimmed = value.trim();
        int hashIndex = indexOfComment(trimmed);
        if (hashIndex >= 0) {
            return trimmed.substring(0, hashIndex).trim();
        }
        return trimmed;
    }

    private static int indexOfComment(String value) {
        int hash = value.indexOf('#');
        int slash = value.indexOf("//");
        if (hash < 0) {
            return slash;
        }
        if (slash < 0) {
            return hash;
        }
        return Math.min(hash, slash);
    }

    private static String stripQuotes(String value) {
        String trimmed = value.trim();
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}
