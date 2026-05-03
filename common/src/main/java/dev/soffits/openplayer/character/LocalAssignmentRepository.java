package dev.soffits.openplayer.character;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public final class LocalAssignmentRepository {
    public static final String FILE_EXTENSION = ".properties";
    private static final Set<String> ALLOWED_FIELDS = Set.of("id", "characterId", "displayName");

    private final Path directory;

    public LocalAssignmentRepository(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("directory cannot be null");
        }
        this.directory = directory;
    }

    public static Path defaultConfigDirectory(Path minecraftConfigDirectory) {
        if (minecraftConfigDirectory == null) {
            throw new IllegalArgumentException("minecraftConfigDirectory cannot be null");
        }
        return minecraftConfigDirectory.resolve("openplayer").resolve("assignments");
    }

    public Path directory() {
        return directory;
    }

    public LocalAssignmentRepositoryResult loadAll(LocalCharacterRepositoryResult characterResult) {
        if (characterResult == null) {
            throw new IllegalArgumentException("characterResult cannot be null");
        }
        List<LocalCharacterValidationError> errors = new ArrayList<>(characterResult.errors());
        Map<String, LocalCharacterDefinition> charactersById = new LinkedHashMap<>();
        for (LocalCharacterDefinition character : characterResult.characters()) {
            charactersById.put(character.id(), character);
        }

        List<LocalAssignmentDefinition> explicitAssignments = new ArrayList<>();
        Set<String> loadedIds = new LinkedHashSet<>();
        for (LocalAssignmentFileResult fileResult : loadExplicitFiles()) {
            if (fileResult.assignment() != null) {
                LocalAssignmentDefinition assignment = fileResult.assignment();
                if (!loadedIds.add(assignment.id())) {
                    errors.add(new LocalCharacterValidationError(fileResult.file(), "Duplicate assignment id: " + assignment.id()));
                } else if (!charactersById.containsKey(assignment.characterId())) {
                    errors.add(new LocalCharacterValidationError(fileResult.file(), "Unknown assignment characterId: " + assignment.characterId()));
                } else if (charactersById.containsKey(assignment.id()) && !assignment.id().equals(assignment.characterId())) {
                    errors.add(new LocalCharacterValidationError(fileResult.file(), "Duplicate assignment id: " + assignment.id()));
                } else {
                    explicitAssignments.add(assignment);
                }
            }
            errors.addAll(fileResult.errors());
        }

        Map<String, LocalAssignmentDefinition> assignmentsById = new LinkedHashMap<>();
        for (LocalAssignmentDefinition assignment : explicitAssignments) {
            assignmentsById.put(assignment.id(), assignment);
        }
        for (LocalCharacterDefinition character : characterResult.characters()) {
            LocalAssignmentDefinition existing = assignmentsById.get(character.id());
            if (existing == null) {
                assignmentsById.put(character.id(), LocalAssignmentDefinition.defaultFor(character));
            }
        }
        return new LocalAssignmentRepositoryResult(List.copyOf(assignmentsById.values()), characterResult.characters(), errors);
    }

    private List<LocalAssignmentFileResult> loadExplicitFiles() {
        if (!Files.exists(directory)) {
            return List.of();
        }
        if (!Files.isDirectory(directory)) {
            return List.of(new LocalAssignmentFileResult(directory, null, List.of(
                    new LocalCharacterValidationError(directory, "Assignment repository path is not a directory")
            )));
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + FILE_EXTENSION)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    files.add(file);
                }
            }
        } catch (IOException exception) {
            return List.of(new LocalAssignmentFileResult(directory, null, List.of(
                    new LocalCharacterValidationError(directory, "Unable to list assignment files")
            )));
        }
        files.sort(Comparator.comparing(path -> path.getFileName().toString()));
        List<LocalAssignmentFileResult> results = new ArrayList<>();
        for (Path file : files) {
            results.add(loadFile(file));
        }
        return results;
    }

    private LocalAssignmentFileResult loadFile(Path file) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IllegalArgumentException exception) {
            return new LocalAssignmentFileResult(file, null, List.of(
                    new LocalCharacterValidationError(file, "Malformed assignment properties: " + exception.getMessage())
            ));
        } catch (IOException exception) {
            return new LocalAssignmentFileResult(file, null, List.of(
                    new LocalCharacterValidationError(file, "Unable to read assignment file")
            ));
        }

        List<LocalCharacterValidationError> errors = validateProperties(file, properties);
        if (!errors.isEmpty()) {
            return new LocalAssignmentFileResult(file, null, errors);
        }
        try {
            return new LocalAssignmentFileResult(file, new LocalAssignmentDefinition(
                    properties.getProperty("id"),
                    properties.getProperty("characterId"),
                    properties.getProperty("displayName")
            ), List.of());
        } catch (IllegalArgumentException exception) {
            return new LocalAssignmentFileResult(file, null, List.of(
                    new LocalCharacterValidationError(file, exception.getMessage())
            ));
        }
    }

    private static List<LocalCharacterValidationError> validateProperties(Path file, Properties properties) {
        List<LocalCharacterValidationError> errors = new ArrayList<>();
        for (Object key : properties.keySet()) {
            String fieldName = String.valueOf(key);
            if (!ALLOWED_FIELDS.contains(fieldName)) {
                errors.add(new LocalCharacterValidationError(file, "Unknown assignment field: " + fieldName));
            }
        }
        for (String message : LocalAssignmentDefinition.validate(
                properties.getProperty("id"),
                properties.getProperty("characterId"),
                properties.getProperty("displayName")
        )) {
            errors.add(new LocalCharacterValidationError(file, message));
        }
        return List.copyOf(errors);
    }

    private record LocalAssignmentFileResult(Path file, LocalAssignmentDefinition assignment,
                                             List<LocalCharacterValidationError> errors) {
    }
}
