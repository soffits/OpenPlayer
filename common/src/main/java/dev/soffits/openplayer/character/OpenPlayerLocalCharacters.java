package dev.soffits.openplayer.character;

import dev.architectury.platform.Platform;
import java.nio.file.Path;

public final class OpenPlayerLocalCharacters {
    private OpenPlayerLocalCharacters() {
    }

    public static Path directory() {
        return LocalCharacterRepository.defaultConfigDirectory(Platform.getConfigFolder());
    }

    public static LocalCharacterRepository repository() {
        return new LocalCharacterRepository(directory());
    }
}
