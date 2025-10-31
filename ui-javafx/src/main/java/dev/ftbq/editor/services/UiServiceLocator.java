package dev.ftbq.editor.services;

import dev.ftbq.editor.assets.CacheManager;
import dev.ftbq.editor.store.StoreDao;
import dev.ftbq.editor.view.graph.layout.JsonQuestLayoutStore;
import dev.ftbq.editor.view.graph.layout.QuestLayoutStore;

import java.nio.file.Path;

public final class UiServiceLocator {
    public static CacheManager cacheManager;
    public static StoreDao storeDao;
    public static QuestLayoutStore questLayoutStore;

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
}


