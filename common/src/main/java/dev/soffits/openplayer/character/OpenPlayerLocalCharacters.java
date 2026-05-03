package dev.soffits.openplayer.character;

import dev.architectury.platform.Platform;
import java.nio.file.Path;

public final class OpenPlayerLocalCharacters {
    private OpenPlayerLocalCharacters() {
    }

    public static Path directory() {
        return openPlayerDirectory().resolve("characters");
    }

    public static Path openPlayerDirectory() {
        return Platform.getConfigFolder().resolve("openplayer");
    }

    public static LocalCharacterRepository repository() {
        return new LocalCharacterRepository(directory());
    }
}
