package dev.ftbq.editor.services.catalog;

import dev.ftbq.editor.ingest.ItemCatalog;
import dev.ftbq.editor.ingest.ItemMeta;
import dev.ftbq.editor.ingest.JarScanner;
import dev.ftbq.editor.services.logging.AppLoggerFactory;
import dev.ftbq.editor.services.logging.StructuredLogger;
import dev.ftbq.editor.store.StoreDao;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Persists ingested {@link ItemCatalog} data into the backing {@link StoreDao} database.
 */
public final class CatalogImportService {

    private final StoreDao storeDao;
    private final StructuredLogger logger;

    public CatalogImportService(StoreDao storeDao, StructuredLogger logger) {
        this.storeDao = Objects.requireNonNull(storeDao, "storeDao");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public CatalogImportService(StoreDao storeDao, AppLoggerFactory loggerFactory) {
        this(storeDao, Objects.requireNonNull(loggerFactory, "loggerFactory").create(CatalogImportService.class));
    }

    /**
     * Import all of the items from the supplied catalog, updating any existing rows as needed.
     *
     * @param catalog ingested catalog to persist
     */
    public void importCatalog(ItemCatalog catalog) {
        Objects.requireNonNull(catalog, "catalog");

        Map<String, List<String>> tagsByItem = buildTagsByItem(catalog.tags());

        logger.info("Importing item catalog",
                StructuredLogger.field("source", catalog.source()),
                StructuredLogger.field("version", catalog.version()),
                StructuredLogger.field("vanilla", catalog.isVanilla()),
                StructuredLogger.field("items", catalog.items().size()),
                StructuredLogger.field("tagGroups", catalog.tags() != null ? catalog.tags().size() : 0));

        if (catalog.items().isEmpty()) {
            logger.warn("Catalog empty, checking for proxy assets in jar source",
                    StructuredLogger.field("source", catalog.source()),
                    StructuredLogger.field("version", catalog.version()));
            Path sourcePath = resolveSourcePath(catalog.source());
            if (sourcePath != null) {
                if (!Files.exists(sourcePath)) {
                    logger.warn("Jar not found for proxy scan",
                            StructuredLogger.field("path", sourcePath.toString()));
                } else {
                    try {
                        List<ItemMeta> proxyItems = JarScanner.extractProxyItems(sourcePath, catalog.version());
                        if (!proxyItems.isEmpty()) {
                            logger.info("Proxy item catalog generated",
                                    StructuredLogger.field("path", sourcePath.toString()),
                                    StructuredLogger.field("count", proxyItems.size()));
                            ItemCatalog proxyCatalog = new ItemCatalog(
                                    sourcePath.toString(),
                                    catalog.version(),
                                    catalog.isVanilla(),
                                    proxyItems,
                                    Map.of()
                            );
                            importCatalog(proxyCatalog);
                            return;
                        } else {
                            logger.info("No proxy items discovered",
                                    StructuredLogger.field("path", sourcePath.toString()));
                        }
                    } catch (IOException e) {
                        logger.warn("Proxy asset import failed", e,
                                StructuredLogger.field("path", sourcePath.toString()));
                    }
                }
            } else {
                logger.warn("Unable to resolve source jar for proxy scan",
                        StructuredLogger.field("source", catalog.source()));
            }
        }

        int upserted = 0;
        for (ItemMeta item : catalog.items()) {
            if (item == null) {
                continue;
            }

            String tags = toJsonArray(tagsByItem.get(item.id()));

            StoreDao.ItemEntity entity = new StoreDao.ItemEntity(
                    item.id(),
                    item.displayName(),
                    item.isVanilla(),
                    normalize(item.modId()),
                    normalize(item.modName()),
                    tags,
                    normalize(item.texturePath()),
                    normalize(item.iconHash()),
                    normalize(catalog.source()),
                    normalize(catalog.version()),
                    item.kind());

            storeDao.upsertItem(entity);
            upserted++;
        }

        logger.info("Catalog import completed",
                StructuredLogger.field("source", catalog.source()),
                StructuredLogger.field("version", catalog.version()),
                StructuredLogger.field("upserted", upserted));
    }

    private Path resolveSourcePath(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        try {
            Path direct = Paths.get(source);
            if (Files.exists(direct)) {
                return direct.toAbsolutePath().normalize();
            }
            Path fallbackBase = Paths.get(System.getProperty("user.dir", ""));
            Path fallback = fallbackBase.resolve(source).normalize();
            if (Files.exists(fallback)) {
                logger.info("Resolved jar path via working directory fallback",
                        StructuredLogger.field("source", source),
                        StructuredLogger.field("resolved", fallback.toString()));
                return fallback;
            }
            return direct.toAbsolutePath().normalize();
        } catch (InvalidPathException ex) {
            logger.warn("Invalid jar path provided for proxy scan", ex,
                    StructuredLogger.field("source", source));
            return null;
        }
    }

    private static Map<String, List<String>> buildTagsByItem(Map<String, List<String>> tags) {
        if (tags == null || tags.isEmpty()) {
            return Map.of();
        }

        Map<String, Set<String>> accumulator = new LinkedHashMap<>();
        tags.forEach((tagId, items) -> {
            if (tagId == null || items == null) {
                return;
            }
            for (String itemId : items) {
                if (itemId == null || itemId.isBlank()) {
                    continue;
                }
                accumulator.computeIfAbsent(itemId, ignored -> new java.util.LinkedHashSet<>()).add(tagId);
            }
        });

        if (accumulator.isEmpty()) {
            return Map.of();
        }

        Map<String, List<String>> result = new LinkedHashMap<>(accumulator.size());
        accumulator.forEach((itemId, tagIds) -> {
            List<String> sorted = new ArrayList<>(tagIds);
            Collections.sort(sorted);
            result.put(itemId, Collections.unmodifiableList(sorted));
        });
        return Collections.unmodifiableMap(result);
    }

    private static String toJsonArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append('"');
            builder.append(escapeJson(values.get(i)));
            builder.append('"');
        }
        builder.append(']');
        return builder.toString();
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch == '"' || ch == '\\') {
                escaped.append('\\');
            }
            escaped.append(ch);
        }
        return escaped.toString();
    }

    private static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
