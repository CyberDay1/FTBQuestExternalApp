package dev.ftbq.editor.services.catalog;

import dev.ftbq.editor.ingest.ItemCatalog;
import dev.ftbq.editor.ingest.ItemMeta;
import dev.ftbq.editor.ingest.JarScanner;
import dev.ftbq.editor.services.logging.AppLoggerFactory;
import dev.ftbq.editor.services.logging.StructuredLogger;
import dev.ftbq.editor.store.StoreDao;
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
                StructuredLogger.field("items", catalog.items().size()));

        if (catalog.items().isEmpty()) {
            logger.warn("Catalog empty, checking for proxy assets in jar source");
            String source = catalog.source();
            if (source != null && !source.isBlank()) {
                try {
                    Path sourcePath = Paths.get(
                            source.contains(":\\") ? source :
                                    System.getProperty("user.dir") + java.io.File.separator + source
                    );
                    logger.info("Resolved JAR path for proxy scan: {}", sourcePath);
                    var proxyItems = JarScanner.extractProxyItems(sourcePath, catalog.version());
                    if (!proxyItems.isEmpty()) {
                        ItemCatalog proxyCatalog = new ItemCatalog(
                                catalog.source(),
                                catalog.version(),
                                catalog.isVanilla(),
                                proxyItems,
                                Map.of()
                        );
                        importCatalog(proxyCatalog);
                        return;
                    }
                } catch (Exception e) {
                    logger.warn("Proxy asset import failed", e);
                }
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
