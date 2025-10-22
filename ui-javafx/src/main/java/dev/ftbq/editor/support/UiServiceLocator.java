package dev.ftbq.editor.support;

import dev.ftbq.editor.assets.CacheManager;
import dev.ftbq.editor.store.Jdbc;
import dev.ftbq.editor.store.StoreDao;

import java.nio.file.Path;
import java.sql.Connection;
import java.util.Objects;

/**
 * Provides lazily-initialised access to shared datastore resources for the UI layer.
 */
public final class UiServiceLocator {
    private static final Path DEFAULT_DATABASE = Path.of("build", "editor.sqlite");

    private static StoreDao storeDao;
    private static Connection connection;
    private static CacheManager cacheManager;

    private UiServiceLocator() {
    }

    public static synchronized StoreDao getStoreDao() {
        if (storeDao == null) {
            connection = Jdbc.open(DEFAULT_DATABASE);
            storeDao = new StoreDao(connection);
        }
        return storeDao;
    }

    public static synchronized CacheManager getCacheManager() {
        if (cacheManager == null) {
            cacheManager = new CacheManager();
        }
        return cacheManager;
    }

    public static synchronized void overrideStoreDao(StoreDao customDao) {
        storeDao = Objects.requireNonNull(customDao, "customDao");
    }

    public static synchronized void overrideCacheManager(CacheManager customCacheManager) {
        cacheManager = Objects.requireNonNull(customCacheManager, "customCacheManager");
    }
}
