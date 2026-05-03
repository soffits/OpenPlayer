package dev.soffits.openplayer.character;

import java.nio.file.Path;

public record LocalCharacterValidationError(Path file, String message) {
    public LocalCharacterValidationError {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message cannot be blank");
        }
    }

    public String describe() {
        return file == null ? message : file + ": " + message;
    }
}
