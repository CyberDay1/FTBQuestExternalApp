package dev.ftbq.editor.services.watch;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Watches a directory and triggers onChange after 500ms of silence. */
public final class FileWatcher implements AutoCloseable {
    private final WatchService watchService;
    private final Path root;
    private final Consumer<Path> onChange;
    private final ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
    private volatile Instant last = Instant.EPOCH;

    public FileWatcher(Path root, Consumer<Path> onChange) throws IOException {
        this.watchService = FileSystems.getDefault().newWatchService();
        this.root = root;
        this.onChange = onChange;
        root.register(watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY,
                StandardWatchEventKinds.ENTRY_DELETE);
        ses.scheduleWithFixedDelay(this::poll, 250, 250, TimeUnit.MILLISECONDS);
    }

    private void poll(){
        WatchKey key;
        while((key = watchService.poll()) != null){
            for(WatchEvent<?> ev : key.pollEvents()){
                last = Instant.now();
            }
            key.reset();
        }
        if(last != Instant.EPOCH && Instant.now().minusMillis(500).isAfter(last)){
            last = Instant.EPOCH;
            onChange.accept(root);
        }
    }

    @Override
    public void close() throws IOException {
        ses.shutdownNow();
        watchService.close();
    }
}
