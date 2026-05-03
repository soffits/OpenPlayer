package dev.soffits.openplayer.character;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public final class LocalCharacterRepository {
    public static final String FILE_EXTENSION = ".properties";
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "id",
            "displayName",
            "description",
            "skinTexture",
            "localSkinFile",
            "defaultRoleId",
            "conversationPrompt",
            "conversationSettings"
    );

    private final Path directory;

    public LocalCharacterRepository(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("directory cannot be null");
        }
        this.directory = directory;
    }

    public Path directory() {
        return directory;
    }

    public static Path defaultConfigDirectory(Path minecraftConfigDirectory) {
        if (minecraftConfigDirectory == null) {
            throw new IllegalArgumentException("minecraftConfigDirectory cannot be null");
        }
        return minecraftConfigDirectory.resolve("openplayer").resolve("characters");
    }

    public LocalCharacterRepositoryResult loadAll() {
        if (!Files.exists(directory)) {
            return new LocalCharacterRepositoryResult(List.of(), List.of());
        }
        if (!Files.isDirectory(directory)) {
            return new LocalCharacterRepositoryResult(List.of(), List.of(
                    new LocalCharacterValidationError(directory, "Character repository path is not a directory")
            ));
        }

        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + FILE_EXTENSION)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    files.add(file);
                }
            }
        } catch (IOException exception) {
            return new LocalCharacterRepositoryResult(List.of(), List.of(
                    new LocalCharacterValidationError(directory, "Unable to list character files: " + exception.getMessage())
            ));
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));

        List<LocalCharacterDefinition> characters = new ArrayList<>();
        List<LocalCharacterValidationError> errors = new ArrayList<>();
        Set<String> loadedIds = new LinkedHashSet<>();
        for (Path file : files) {
            LocalCharacterFileResult fileResult = loadFile(file);
            if (fileResult.character() != null) {
                if (!loadedIds.add(fileResult.character().id())) {
                    errors.add(new LocalCharacterValidationError(file, "Duplicate character id: " + fileResult.character().id()));
                } else {
                    characters.add(fileResult.character());
                }
            }
            errors.addAll(fileResult.errors());
        }
        return new LocalCharacterRepositoryResult(characters, errors);
    }

    public void save(LocalCharacterDefinition character) throws IOException {
        if (character == null) {
            throw new IllegalArgumentException("character cannot be null");
        }
        Files.createDirectories(directory);
        Path target = directory.resolve(character.id() + FILE_EXTENSION).normalize();
        if (!target.startsWith(directory.normalize())) {
            throw new IOException("Character path escapes repository directory");
        }

        Properties properties = new Properties();
        properties.setProperty("id", character.id());
        properties.setProperty("displayName", character.displayName());
        character.optionalDescription().ifPresent(value -> properties.setProperty("description", value));
        character.optionalSkinTexture().ifPresent(value -> properties.setProperty("skinTexture", value));
        character.optionalLocalSkinFile().ifPresent(value -> properties.setProperty("localSkinFile", value));
        character.optionalDefaultRoleId().ifPresent(value -> properties.setProperty("defaultRoleId", value));
        character.optionalConversationPrompt().ifPresent(value -> properties.setProperty("conversationPrompt", value));
        character.optionalConversationSettings().ifPresent(value -> properties.setProperty("conversationSettings", value));

        Path tempFile = Files.createTempFile(directory, character.id() + "-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8)) {
                properties.store(writer, "OpenPlayer local character");
            }
            try {
                Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private LocalCharacterFileResult loadFile(Path file) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IllegalArgumentException exception) {
            return new LocalCharacterFileResult(null, List.of(
                    new LocalCharacterValidationError(file, "Malformed character properties: " + exception.getMessage())
            ));
        } catch (IOException exception) {
            return new LocalCharacterFileResult(null, List.of(
                    new LocalCharacterValidationError(file, "Unable to read character file: " + exception.getMessage())
            ));
        }

        List<LocalCharacterValidationError> errors = validateProperties(file, properties);
        if (!errors.isEmpty()) {
            return new LocalCharacterFileResult(null, errors);
        }

        try {
            return new LocalCharacterFileResult(new LocalCharacterDefinition(
                    properties.getProperty("id"),
                    properties.getProperty("displayName"),
                    properties.getProperty("description"),
                    properties.getProperty("skinTexture"),
                    properties.getProperty("localSkinFile"),
                    properties.getProperty("defaultRoleId"),
                    properties.getProperty("conversationPrompt"),
                    properties.getProperty("conversationSettings")
            ), List.of());
        } catch (IllegalArgumentException exception) {
            return new LocalCharacterFileResult(null, List.of(
                    new LocalCharacterValidationError(file, exception.getMessage())
            ));
        }
    }

    private static List<LocalCharacterValidationError> validateProperties(Path file, Properties properties) {
        List<LocalCharacterValidationError> errors = new ArrayList<>();
        for (Object key : properties.keySet()) {
            String fieldName = String.valueOf(key);
            if (!ALLOWED_FIELDS.contains(fieldName)) {
                errors.add(new LocalCharacterValidationError(file, "Unknown character field: " + fieldName));
            }
        }
        for (String message : LocalCharacterDefinition.validate(
                properties.getProperty("id"),
                properties.getProperty("displayName"),
                properties.getProperty("description"),
                properties.getProperty("skinTexture"),
                properties.getProperty("localSkinFile"),
                properties.getProperty("defaultRoleId"),
                properties.getProperty("conversationPrompt"),
                properties.getProperty("conversationSettings")
        )) {
            errors.add(new LocalCharacterValidationError(file, message));
        }
        return List.copyOf(errors);
    }

    private record LocalCharacterFileResult(LocalCharacterDefinition character,
                                            List<LocalCharacterValidationError> errors) {
    }
}
