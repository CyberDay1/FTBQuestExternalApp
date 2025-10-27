package dev.ftbq.editor.services;

import dev.ftbq.editor.assets.CacheManager;
import dev.ftbq.editor.store.StoreDao;

public final class UiServiceLocator {
    public static CacheManager cacheManager;
    public static StoreDao storeDao;

    private UiServiceLocator() {
    }

    public static void init(CacheManager cm, StoreDao dao) {
        cacheManager = cm;
        storeDao = dao;
    }
}


