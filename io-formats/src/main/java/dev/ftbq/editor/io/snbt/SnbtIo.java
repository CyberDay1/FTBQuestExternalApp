package dev.ftbq.editor.io.snbt;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Utility methods for reading and writing SNBT quest data.
 */
public final class SnbtIo {

    private SnbtIo() {
    }

    public static String read(File file) throws IOException {
        try (var in = new FileInputStream(file)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static void write(File file, String snbt) throws IOException {
        var parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        try (var out = new FileOutputStream(file)) {
            out.write(snbt.getBytes(StandardCharsets.UTF_8));
        }
    }
}
