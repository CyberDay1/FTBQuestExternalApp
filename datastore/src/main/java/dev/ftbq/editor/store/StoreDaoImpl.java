package dev.ftbq.editor.store;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Convenience {@link StoreDao} implementation used by the JavaFX UI.
 */
public final class StoreDaoImpl extends StoreDao {

    private static final Logger LOGGER = Logger.getLogger(StoreDaoImpl.class.getName());
    private static final Path DEFAULT_DATABASE = Path.of(System.getProperty("user.home"), ".ftbq-editor", "editor.sqlite");

    private final Path databasePath;

    public StoreDaoImpl() {
        this(DEFAULT_DATABASE);
    }

    public StoreDaoImpl(Path databasePath) {
        super(openConnection(Objects.requireNonNull(databasePath, "databasePath")));
        this.databasePath = databasePath.toAbsolutePath();
    }

    public Path getDatabasePath() {
        return databasePath;
    }

    /**
     * Attempts to preload the most recently used project so that dependent controllers
     * can access quest data immediately after application startup. The current
     * implementation simply ensures that the backing database exists.
     */
    public void loadLastProjectIfAvailable() {
        try {
            if (Files.notExists(databasePath)) {
                Path parent = databasePath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.createFile(databasePath);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to initialise quest datastore", ex);
        }
    }

    private static Connection openConnection(Path path) {
        return Jdbc.open(path);
    }
}
