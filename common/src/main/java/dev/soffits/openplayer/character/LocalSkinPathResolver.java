package dev.soffits.openplayer.character;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;

public final class LocalSkinPathResolver {
    public static final String SKIN_DIRECTORY_NAME = "skins";
    private final Path openPlayerDirectory;
    private final Path skinDirectory;

    public LocalSkinPathResolver(Path openPlayerDirectory) {
        if (openPlayerDirectory == null) {
            throw new IllegalArgumentException("openPlayerDirectory cannot be null");
        }
        this.openPlayerDirectory = openPlayerDirectory.toAbsolutePath().normalize();
        this.skinDirectory = this.openPlayerDirectory.resolve(SKIN_DIRECTORY_NAME).normalize();
    }

    public Path skinDirectory() {
        return skinDirectory;
    }

    public LocalSkinPathResolution resolve(String localSkinFile) {
        if (localSkinFile == null || localSkinFile.isBlank()) {
            return LocalSkinPathResolution.rejected("localSkinFile is not configured");
        }
        List<String> validationErrors = LocalCharacterDefinition.validateLocalSkinFile(localSkinFile);
        if (!validationErrors.isEmpty()) {
            return LocalSkinPathResolution.rejected(validationErrors.get(0));
        }
        Path candidate = openPlayerDirectory.resolve(localSkinFile).normalize();
        if (!candidate.startsWith(skinDirectory)) {
            return LocalSkinPathResolution.rejected("localSkinFile must stay under the skins directory");
        }
        if (Files.isSymbolicLink(skinDirectory)) {
            return LocalSkinPathResolution.rejected("localSkinFile must stay under the skins directory");
        }
        Path realSkinDirectory;
        Path realCandidate;
        try {
            realSkinDirectory = skinDirectory.toRealPath();
            realCandidate = candidate.toRealPath();
        } catch (NoSuchFileException exception) {
            return LocalSkinPathResolution.rejected("localSkinFile does not exist");
        } catch (IOException | SecurityException exception) {
            return LocalSkinPathResolution.rejected("localSkinFile is not loadable");
        }
        if (!realCandidate.startsWith(realSkinDirectory)) {
            return LocalSkinPathResolution.rejected("localSkinFile must stay under the skins directory");
        }
        if (!Files.isRegularFile(realCandidate, LinkOption.NOFOLLOW_LINKS)) {
            return LocalSkinPathResolution.rejected("localSkinFile must be a regular PNG file");
        }
        return LocalSkinPathResolution.resolved(realCandidate);
    }
}
