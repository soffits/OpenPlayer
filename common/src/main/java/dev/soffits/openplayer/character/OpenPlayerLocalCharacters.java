package dev.soffits.openplayer.character;

import dev.architectury.platform.Platform;
import java.nio.file.Path;

public final class OpenPlayerLocalCharacters {
    private OpenPlayerLocalCharacters() {
    }

    public static Path directory() {
        return openPlayerDirectory().resolve("characters");
    }

    public static Path assignmentsDirectory() {
        return openPlayerDirectory().resolve("assignments");
    }

    public static Path importsDirectory() {
        return openPlayerDirectory().resolve("imports");
    }

    public static Path exportsDirectory() {
        return openPlayerDirectory().resolve("exports");
    }

    public static Path openPlayerDirectory() {
        return Platform.getConfigFolder().resolve("openplayer");
    }

    public static LocalCharacterRepository repository() {
        return new LocalCharacterRepository(directory());
    }

    public static LocalAssignmentRepository assignmentRepository() {
        return new LocalAssignmentRepository(assignmentsDirectory());
    }
}
