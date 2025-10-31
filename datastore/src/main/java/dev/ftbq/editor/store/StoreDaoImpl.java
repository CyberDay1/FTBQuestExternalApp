package dev.ftbq.editor.store;

import dev.ftbq.editor.domain.Chapter;
import dev.ftbq.editor.domain.IconRef;
import dev.ftbq.editor.domain.ItemRef;
import dev.ftbq.editor.domain.ItemReward;
import dev.ftbq.editor.domain.ItemTask;
import dev.ftbq.editor.domain.Quest;
import dev.ftbq.editor.domain.QuestFile;
import dev.ftbq.editor.domain.Visibility;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
     * can access quest data immediately after application startup. If the datastore
     * has not been populated yet, a starter project is created in-memory so that the
     * UI can still offer meaningful interactions.
     */
    public void loadLastProjectIfAvailable() {
        try {
            ensureDatabaseExists();
            Project project = resolveActiveProject();
            setActiveProject(project);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to initialise quest datastore", ex);
        }
    }

    private void ensureDatabaseExists() throws Exception {
        if (Files.notExists(databasePath)) {
            Path parent = databasePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.createFile(databasePath);
        }
    }

    private Project resolveActiveProject() {
        List<Chapter> chapters = loadChapters();
        if (chapters.isEmpty()) {
            return createDefaultProject();
        }
        QuestFile questFile = buildQuestFileFromStore(chapters);
        return new Project(questFile);
    }

    private QuestFile buildQuestFileFromStore(List<Chapter> chapters) {
        String baseName = Optional.ofNullable(databasePath.getFileName())
                .map(Path::toString)
                .map(name -> name.replaceFirst("\\.sqlite$", ""))
                .filter(name -> !name.isBlank())
                .orElse("quest_project");
        return QuestFile.builder()
                .id(baseName)
                .title("Project: " + baseName)
                .chapters(chapters)
                .chapterGroups(List.of())
                .lootTables(List.of())
                .build();
    }

    private Project createDefaultProject() {
        Quest welcomeQuest = Quest.builder()
                .id("welcome")
                .title("Welcome to the Editor")
                .description("Double-click a chapter to open its quests.")
                .icon(new IconRef("minecraft:book"))
                .tasks(List.of(new ItemTask(new ItemRef("minecraft:oak_log", 8), true)))
                .itemRewards(List.of(new ItemReward(new ItemRef("minecraft:torch", 8))))
                .visibility(Visibility.VISIBLE)
                .build();

        Chapter starterChapter = Chapter.builder()
                .id("getting_started")
                .title("Getting Started")
                .addQuest(welcomeQuest)
                .visibility(Visibility.VISIBLE)
                .build();

        QuestFile questFile = QuestFile.builder()
                .id("sample_project")
                .title("Sample Project")
                .chapters(List.of(starterChapter))
                .chapterGroups(List.of())
                .lootTables(List.of())
                .build();
        return new Project(questFile);
    }

    private static Connection openConnection(Path path) {
        return Jdbc.open(path);
    }
}
