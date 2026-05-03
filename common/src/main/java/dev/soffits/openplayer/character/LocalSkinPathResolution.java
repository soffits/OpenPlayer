package dev.soffits.openplayer.character;

import java.nio.file.Path;
import java.util.Optional;

public record LocalSkinPathResolution(Path path, String rejectionReason) {
    public LocalSkinPathResolution {
        if (path != null && rejectionReason != null) {
            throw new IllegalArgumentException("resolved skin path cannot also have a rejection reason");
        }
    }

    public static LocalSkinPathResolution resolved(Path path) {
        if (path == null) {
            throw new IllegalArgumentException("path cannot be null");
        }
        return new LocalSkinPathResolution(path, null);
    }

    public static LocalSkinPathResolution rejected(String reason) {
        String safeReason = reason == null || reason.isBlank() ? "localSkinFile is not loadable" : reason;
        return new LocalSkinPathResolution(null, safeReason);
    }

    public boolean isResolved() {
        return path != null;
    }

    public Optional<Path> optionalPath() {
        return Optional.ofNullable(path);
    }
}
