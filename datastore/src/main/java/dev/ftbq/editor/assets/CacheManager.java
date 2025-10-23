package dev.ftbq.editor.assets;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CacheManager {

    private static final String ICON_EXTENSION = ".icon";
    private static final String BACKGROUND_EXTENSION = ".background";

    private static final int DEFAULT_MAX_ICON_ENTRIES = 512;
    private static final int DEFAULT_MAX_BACKGROUND_ENTRIES = 128;

    private final Path rootDirectory;
    private final Path iconDirectory;
    private final Path backgroundDirectory;
    private final int maxIconEntries;
    private final int maxBackgroundEntries;
    private final AtomicLong logicalClock = new AtomicLong();

    private final Object iconLock = new Object();
    private final Object backgroundLock = new Object();

    public CacheManager() {
        this(Paths.get(".cache"));
    }

    public CacheManager(Path rootDirectory) {
        this(rootDirectory, DEFAULT_MAX_ICON_ENTRIES, DEFAULT_MAX_BACKGROUND_ENTRIES);
    }

    public CacheManager(Path rootDirectory, int maxIconEntries, int maxBackgroundEntries) {
        this.rootDirectory = Objects.requireNonNull(rootDirectory, "rootDirectory");
        this.maxIconEntries = maxIconEntries;
        this.maxBackgroundEntries = maxBackgroundEntries;
        this.iconDirectory = rootDirectory.resolve("icons");
        this.backgroundDirectory = rootDirectory.resolve("backgrounds");
        initialiseDirectories();
        seedLogicalClock();
    }

    public String storeIcon(byte[] data) {
        Objects.requireNonNull(data, "data");
        String hash = hashBytes(data);
        Path iconPath = iconDirectory.resolve(hash + ICON_EXTENSION);
        synchronized (iconLock) {
            writeIfNecessary(iconPath, data);
            touch(iconPath);
            enforceLimit(iconDirectory, maxIconEntries);
        }
        return hash;
    }

    public Optional<byte[]> fetchIcon(String hash) {
        Objects.requireNonNull(hash, "hash");
        Path iconPath = iconDirectory.resolve(hash + ICON_EXTENSION);
        synchronized (iconLock) {
            if (!Files.exists(iconPath)) {
                return Optional.empty();
            }
            try {
                byte[] data = Files.readAllBytes(iconPath);
                touch(iconPath);
                return Optional.of(data);
            } catch (IOException ex) {
                throw new CacheOperationException("Failed to read cached icon", ex);
            }
        }
    }

    public String storeBackground(byte[] data) {
        Objects.requireNonNull(data, "data");
        String hash = hashBytes(data);
        Path backgroundPath = backgroundDirectory.resolve(hash + BACKGROUND_EXTENSION);
        synchronized (backgroundLock) {
            writeIfNecessary(backgroundPath, data);
            touch(backgroundPath);
            enforceLimit(backgroundDirectory, maxBackgroundEntries);
        }
        return hash;
    }

    public Optional<byte[]> fetchBackground(String hash) {
        Objects.requireNonNull(hash, "hash");
        Path backgroundPath = backgroundDirectory.resolve(hash + BACKGROUND_EXTENSION);
        synchronized (backgroundLock) {
            if (!Files.exists(backgroundPath)) {
                return Optional.empty();
            }
            try {
                byte[] data = Files.readAllBytes(backgroundPath);
                touch(backgroundPath);
                return Optional.of(data);
            } catch (IOException ex) {
                throw new CacheOperationException("Failed to read cached background", ex);
            }
        }
    }

    public Path resolveBackground(String hash) {
        Objects.requireNonNull(hash, "hash");
        return backgroundDirectory.resolve(hash + BACKGROUND_EXTENSION);
    }

    public void clearIcons() {
        synchronized (iconLock) {
            purgeDirectory(iconDirectory);
        }
    }

    private void initialiseDirectories() {
        try {
            Files.createDirectories(rootDirectory);
            Files.createDirectories(iconDirectory);
            Files.createDirectories(backgroundDirectory);
        } catch (IOException ex) {
            throw new CacheOperationException("Failed to initialise cache directories", ex);
        }
    }

    private void seedLogicalClock() {
        long currentTime = System.currentTimeMillis();
        long latestTimestamp = Stream
                .of(iconDirectory, backgroundDirectory)
                .flatMap(directory -> listRegularFiles(directory).stream())
                .mapToLong(path -> {
                    try {
                        return Files.getLastModifiedTime(path).toMillis();
                    } catch (IOException ex) {
                        return Long.MIN_VALUE;
                    }
                })
                .filter(value -> value != Long.MIN_VALUE)
                .max()
                .orElse(currentTime);

        logicalClock.set(Math.max(currentTime, latestTimestamp));
    }

    private void writeIfNecessary(Path path, byte[] data) {
        try {
            if (Files.exists(path)) {
                if (!isContentIdentical(path, data)) {
                    Files.write(path, data);
                }
            } else {
                Files.write(path, data);
            }
        } catch (IOException ex) {
            throw new CacheOperationException("Failed to write cache entry", ex);
        }
    }

    private boolean isContentIdentical(Path path, byte[] data) throws IOException {
        byte[] existing = Files.readAllBytes(path);
        if (existing.length != data.length) {
            return false;
        }
        for (int i = 0; i < existing.length; i++) {
            if (existing[i] != data[i]) {
                return false;
            }
        }
        return true;
    }

    private void touch(Path path) {
        long timestamp = nextTimestamp();
        try {
            Files.setLastModifiedTime(path, FileTime.fromMillis(timestamp));
        } catch (IOException ignored) {
            // Fallback best effort.
        }
    }

    private long nextTimestamp() {
        long now = System.currentTimeMillis();
        return logicalClock.updateAndGet(previous -> now > previous ? now : previous + 1);
    }

    private void enforceLimit(Path directory, int maxEntries) {
        if (maxEntries <= 0) {
            purgeDirectory(directory);
            return;
        }

        List<Path> entries = listRegularFiles(directory);
        int overflow = entries.size() - maxEntries;
        if (overflow <= 0) {
            return;
        }

        entries.sort(Comparator.comparing(path -> {
            try {
                return Files.getLastModifiedTime(path).toMillis();
            } catch (IOException ex) {
                return Long.MIN_VALUE;
            }
        }));

        for (int i = 0; i < overflow; i++) {
            try {
                Files.deleteIfExists(entries.get(i));
            } catch (IOException ignored) {
                // Best effort deletion.
            }
        }
    }

    private void purgeDirectory(Path directory) {
        for (Path path : listRegularFiles(directory)) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // ignore
            }
        }
    }

    private List<Path> listRegularFiles(Path directory) {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.filter(Files::isRegularFile).collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException ex) {
            throw new CacheOperationException("Failed to list cache directory", ex);
        }
    }

    private String hashBytes(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
            return toHex(hashBytes);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest not available", ex);
        }
    }

    private String toHex(byte[] input) {
        StringBuilder builder = new StringBuilder(input.length * 2);
        for (byte b : input) {
            builder.append(String.format(Locale.ROOT, "%02x", b));
        }
        return builder.toString();
    }

    public static final class CacheOperationException extends RuntimeException {
        public CacheOperationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
