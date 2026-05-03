package dev.soffits.openplayer.debug;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

public final class OpenPlayerDebugFileSink {
    public static final long MAX_FILE_BYTES = 1024L * 1024L;
    private final Path file;
    private final Path rotatedFile;

    public OpenPlayerDebugFileSink(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("directory cannot be null");
        }
        this.file = directory.resolve("events.jsonl");
        this.rotatedFile = directory.resolve("events.previous.jsonl");
    }

    public void append(OpenPlayerDebugEvent event) {
        if (event == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            rotateIfNeeded();
            Files.writeString(
                    file,
                    event.jsonLine() + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            );
        } catch (IOException | SecurityException ignored) {
            // Debug logging must never affect gameplay.
        }
    }

    Path file() {
        return file;
    }

    private void rotateIfNeeded() throws IOException {
        if (Files.isRegularFile(file) && Files.size(file) >= MAX_FILE_BYTES) {
            Files.move(file, rotatedFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
