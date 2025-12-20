package dev.ftbq.editor.services;

import dev.ftbq.editor.assets.CacheManager;
import dev.ftbq.editor.domain.version.VersionCatalog;
import dev.ftbq.editor.services.catalog.CatalogImportService;
import dev.ftbq.editor.services.logging.AppLoggerFactory;
import dev.ftbq.editor.services.bus.ServiceLocator;
import dev.ftbq.editor.services.mods.ModRegistryService;
import dev.ftbq.editor.store.StoreDao;
import dev.ftbq.editor.support.StoreBackedVersionCatalog;
import dev.ftbq.editor.view.graph.layout.JsonQuestLayoutStore;
import dev.ftbq.editor.view.graph.layout.QuestLayoutStore;

import java.nio.file.Path;
import java.util.Objects;

public final class UiServiceLocator {
    public static CacheManager cacheManager;
    public static StoreDao storeDao;
    public static QuestLayoutStore questLayoutStore;

    private static VersionCatalog versionCatalog;
    private static ModRegistryService modRegistryService;
    private static CatalogImportService catalogImportService;

    private UiServiceLocator() {
    }

    public static void initialize() {
        if (cacheManager == null) {
            cacheManager = new CacheManager();
        }
        if (questLayoutStore == null) {
            questLayoutStore = new JsonQuestLayoutStore(Path.of(System.getProperty("user.dir")));
        }
    }

    public static void init(CacheManager cm, StoreDao dao) {
        init(cm, dao, null);
    }

    public static void init(CacheManager cm, StoreDao dao, QuestLayoutStore layoutStore) {
        cacheManager = cm;
        storeDao = dao;
        questLayoutStore = layoutStore;
    }

    public static synchronized CacheManager getCacheManager() {
        if (cacheManager == null) {
            cacheManager = new CacheManager();
        }
        return cacheManager;
    }

    public static synchronized StoreDao getStoreDao() {
        return storeDao;
    }

    public static synchronized VersionCatalog getVersionCatalog() {
        if (versionCatalog == null && storeDao != null) {
            versionCatalog = new StoreBackedVersionCatalog(storeDao);
        }
        return versionCatalog;
    }

    public static synchronized void rebuildVersionCatalog() {
        versionCatalog = null;
    }

    public static synchronized ModRegistryService getModRegistryService() {
        if (modRegistryService == null) {
            modRegistryService = new ModRegistryService();
        }
        return modRegistryService;
    }

    public static synchronized CatalogImportService getCatalogImportService() {
        if (catalogImportService == null && storeDao != null) {
            catalogImportService = new CatalogImportService(storeDao, ServiceLocator.loggerFactory());
        }
        return catalogImportService;
    }

    public static synchronized void overrideStoreDao(StoreDao customDao) {
        storeDao = Objects.requireNonNull(customDao, "customDao");
    }

    public static synchronized void overrideCacheManager(CacheManager customCacheManager) {
        cacheManager = Objects.requireNonNull(customCacheManager, "customCacheManager");
    }

    public static synchronized void overrideVersionCatalog(VersionCatalog customCatalog) {
        versionCatalog = Objects.requireNonNull(customCatalog, "customCatalog");
    }

    public static synchronized void overrideModRegistryService(ModRegistryService customService) {
        modRegistryService = Objects.requireNonNull(customService, "customService");
    }
}


