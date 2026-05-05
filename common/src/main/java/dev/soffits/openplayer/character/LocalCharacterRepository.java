package dev.soffits.openplayer.character;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

public final class LocalCharacterRepository {
    public static final String FILE_EXTENSION = ".properties";
    private static final Pattern SAFE_CHARACTER_FILE_NAME = Pattern.compile("[a-z0-9][a-z0-9_-]{1,63}\\.properties");
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "id",
            "displayName",
            "description",
            "skinTexture",
            "localSkinFile",
            "defaultRoleId",
            "conversationPrompt",
            "conversationSettings",
            "allowWorldActions",
            "movementPolicy"
    );

    private final Path directory;
    private final String protectedCharacterId;

    public LocalCharacterRepository(Path directory) {
        this(directory, "openplayer_default");
    }

    public LocalCharacterRepository(Path directory, String protectedCharacterId) {
        if (directory == null) {
            throw new IllegalArgumentException("directory cannot be null");
        }
        this.directory = directory;
        this.protectedCharacterId = protectedCharacterId == null ? "" : protectedCharacterId.trim();
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

    public static Path defaultImportDirectory(Path minecraftConfigDirectory) {
        if (minecraftConfigDirectory == null) {
            throw new IllegalArgumentException("minecraftConfigDirectory cannot be null");
        }
        return minecraftConfigDirectory.resolve("openplayer").resolve("imports");
    }

    public static Path defaultExportDirectory(Path minecraftConfigDirectory) {
        if (minecraftConfigDirectory == null) {
            throw new IllegalArgumentException("minecraftConfigDirectory cannot be null");
        }
        return minecraftConfigDirectory.resolve("openplayer").resolve("exports");
    }

    public LocalCharacterRepositoryResult loadAll() {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return new LocalCharacterRepositoryResult(List.of(), List.of());
        }
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            return new LocalCharacterRepositoryResult(List.of(), List.of(
                    new LocalCharacterValidationError(directory, "Character repository path is not a directory")
            ));
        }

        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*" + FILE_EXTENSION)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    files.add(file);
                }
            }
        } catch (IOException exception) {
            return new LocalCharacterRepositoryResult(List.of(), List.of(
                    new LocalCharacterValidationError(directory, "Unable to list character files")
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

    public LocalCharacterFileOperationResult save(LocalCharacterDefinition character) {
        if (character == null) {
            throw new IllegalArgumentException("character cannot be null");
        }
        String fileName = character.id() + FILE_EXTENSION;
        LocalCharacterFileOperationResult validation = validateCharacterForWrite(character, fileName);
        if (validation != null) {
            return validation;
        }
        try {
            ensureWritableDirectory(directory);
            Path target = safeChild(directory, fileName);
            rejectSymbolicLinkTarget(target);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                LocalCharacterFileResult existing = loadFile(target);
                if (existing.character() == null || !existing.errors().isEmpty()) {
                    return rejected(fileName, "Existing character file is invalid and was not overwritten");
                }
                if (!existing.character().id().equals(character.id())) {
                    return rejected(fileName, "Existing character file belongs to another id");
                }
            }
            writeCharacterFile(directory, target, character);
            return new LocalCharacterFileOperationResult(LocalCharacterFileOperationStatus.SAVED, fileName,
                    "Saved local character " + character.id());
        } catch (UnsafeCharacterPathException exception) {
            return rejected(fileName, "Character file path is not safe");
        } catch (IOException exception) {
            return new LocalCharacterFileOperationResult(LocalCharacterFileOperationStatus.FAILED, fileName,
                    "Unable to save local character");
        }
    }

    public LocalCharacterFileOperationResult importFrom(Path openPlayerDirectory, String fileName) {
        Path importDirectory = openPlayerDirectory == null ? null : openPlayerDirectory.resolve("imports");
        return importFromDirectory(importDirectory, fileName);
    }

    public LocalCharacterFileOperationResult importFromDirectory(Path importDirectory, String fileName) {
        return importFromDirectory(importDirectory, fileName, false);
    }

    public LocalCharacterFileOperationResult importFromDirectory(Path importDirectory, String fileName, boolean removeSourceAfterSuccess) {
        LocalCharacterFileOperationResult fileNameValidation = validateSafeCharacterFileName(fileName);
        if (fileNameValidation != null) {
            return fileNameValidation;
        }
        try {
            ensureReadableDirectory(importDirectory);
            Path source = safeChild(importDirectory, fileName);
            rejectSymbolicLinkTarget(source);
            LocalCharacterFileResult imported = loadFile(source);
            if (imported.character() == null || !imported.errors().isEmpty()) {
                return rejected(fileName, safeErrorMessage(imported.errors(), "Imported character file is invalid"));
            }
            LocalCharacterFileOperationResult saved = save(imported.character());
            if (!saved.succeeded()) {
                return new LocalCharacterFileOperationResult(saved.status(), fileName, saved.message());
            }
            if (removeSourceAfterSuccess) {
                rejectSymbolicLinkTarget(source);
                Files.delete(source);
            }
            return new LocalCharacterFileOperationResult(LocalCharacterFileOperationStatus.IMPORTED, fileName,
                    "Imported local character " + imported.character().id());
        } catch (UnsafeCharacterPathException exception) {
            return rejected(fileName, "Import file path is not safe");
        } catch (IOException exception) {
            return new LocalCharacterFileOperationResult(LocalCharacterFileOperationStatus.FAILED, safeFileName(fileName),
                    "Unable to import local character");
        }
    }

    public List<String> listImportFileNames(Path importDirectory) {
        try {
            ensureReadableDirectory(importDirectory);
        } catch (IOException exception) {
            return List.of();
        }
        List<String> fileNames = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(importDirectory, "*" + FILE_EXTENSION)) {
            for (Path file : stream) {
                String fileName = file.getFileName() == null ? "" : file.getFileName().toString();
                if (Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isSymbolicLink(file)
                        && validateSafeCharacterFileName(fileName) == null) {
                    fileNames.add(fileName);
                }
            }
        } catch (IOException exception) {
            return List.of();
        }
        fileNames.sort(String::compareTo);
        return List.copyOf(fileNames);
    }

    public LocalCharacterFileOperationResult delete(String characterId) {
        String normalizedId = characterId == null ? "" : characterId.trim();
        String fileName = normalizedId + FILE_EXTENSION;
        if (!protectedCharacterId.isBlank() && protectedCharacterId.equals(normalizedId)) {
            return rejected(fileName, "The default local profile cannot be deleted");
        }
        LocalCharacterFileOperationResult fileNameValidation = validateSafeCharacterFileName(fileName);
        if (fileNameValidation != null) {
            return rejected(safeFileName(fileName), "Character id is not safe for deletion");
        }
        try {
            ensureReadableDirectory(directory);
            Path target = safeChild(directory, fileName);
            rejectSymbolicLinkTarget(target);
            LocalCharacterFileResult loaded = loadFile(target);
            if (loaded.character() == null || !loaded.errors().isEmpty()) {
                return rejected(fileName, safeErrorMessage(loaded.errors(), "Local character is not valid for deletion"));
            }
            if (!loaded.character().id().equals(normalizedId)) {
                return rejected(fileName, "Local character id does not match its file name");
            }
            Files.delete(target);
            return new LocalCharacterFileOperationResult(LocalCharacterFileOperationStatus.DELETED, fileName,
                    "Deleted local character " + normalizedId);
        } catch (UnsafeCharacterPathException exception) {
            return rejected(fileName, "Delete file path is not safe");
        } catch (IOException exception) {
            return new LocalCharacterFileOperationResult(LocalCharacterFileOperationStatus.FAILED, fileName,
                    "Unable to delete local character");
        }
    }

    public LocalCharacterFileOperationResult exportTo(Path openPlayerDirectory, String characterId) {
        Path exportDirectory = openPlayerDirectory == null ? null : openPlayerDirectory.resolve("exports");
        return exportToDirectory(exportDirectory, characterId);
    }

    public LocalCharacterFileOperationResult exportToDirectory(Path exportDirectory, String characterId) {
        String normalizedId = characterId == null ? "" : characterId.trim();
        String fileName = normalizedId + FILE_EXTENSION;
        LocalCharacterFileOperationResult fileNameValidation = validateSafeCharacterFileName(fileName);
        if (fileNameValidation != null) {
            return rejected(safeFileName(fileName), "Character id is not safe for export");
        }
        try {
            if (exportDirectory == null) {
                throw new IOException("Directory is unavailable");
            }
            ensureReadableDirectory(directory);
            Path source = safeChild(directory, fileName);
            rejectSymbolicLinkTarget(source);
            LocalCharacterFileResult loaded = loadFile(source);
            if (loaded.character() == null || !loaded.errors().isEmpty()) {
                return rejected(fileName, safeErrorMessage(loaded.errors(), "Local character is not valid for export"));
            }
            if (!loaded.character().id().equals(normalizedId)) {
                return rejected(fileName, "Local character id does not match its file name");
            }
            ensureWritableDirectory(exportDirectory);
            Path target = safeChild(exportDirectory, fileName);
            rejectSymbolicLinkTarget(target);
            writeCharacterFile(exportDirectory, target, loaded.character());
            return new LocalCharacterFileOperationResult(LocalCharacterFileOperationStatus.EXPORTED, fileName,
                    "Exported local character " + normalizedId);
        } catch (UnsafeCharacterPathException exception) {
            return rejected(fileName, "Export file path is not safe");
        } catch (IOException exception) {
            return new LocalCharacterFileOperationResult(LocalCharacterFileOperationStatus.FAILED, fileName,
                    "Unable to export local character");
        }
    }

    static LocalCharacterFileOperationResult validateSafeCharacterFileName(String fileName) {
        String normalized = fileName == null ? "" : fileName.trim();
        if (normalized.isBlank()
                || normalized.startsWith(".")
                || normalized.startsWith("/")
                || normalized.startsWith("\\")
                || normalized.contains("/")
                || normalized.contains("\\")
                || normalized.contains(":")
                || normalized.contains("..")
                || !SAFE_CHARACTER_FILE_NAME.matcher(normalized).matches()) {
            return rejected(safeFileName(normalized), "File name must be a safe .properties character file name");
        }
        return null;
    }

    private static LocalCharacterFileOperationResult validateCharacterForWrite(LocalCharacterDefinition character, String fileName) {
        LocalCharacterFileOperationResult fileNameValidation = validateSafeCharacterFileName(fileName);
        if (fileNameValidation != null) {
            return rejected(safeFileName(fileName), "Character id is not safe for writing");
        }
        List<String> errors = LocalCharacterDefinition.validate(character.id(), character.displayName(), character.description(),
                character.skinTexture(), character.localSkinFile(), character.defaultRoleId(), character.conversationPrompt(),
                character.conversationSettings(), character.movementPolicy());
        if (!errors.isEmpty()) {
            return rejected(fileName, String.join("; ", errors));
        }
        return null;
    }

    private static void writeCharacterFile(Path tempDirectory, Path target, LocalCharacterDefinition character) throws IOException {
        ensureWritableDirectory(tempDirectory);
        rejectSymbolicLinkTarget(target);
        Path tempFile = Files.createTempFile(tempDirectory, character.id() + "-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) {
                writeProperty(writer, "id", character.id());
                writeProperty(writer, "displayName", character.displayName());
                writeOptionalProperty(writer, "description", character.description());
                writeOptionalProperty(writer, "skinTexture", character.skinTexture());
                writeOptionalProperty(writer, "localSkinFile", character.localSkinFile());
                writeOptionalProperty(writer, "defaultRoleId", character.defaultRoleId());
                writeOptionalProperty(writer, "conversationPrompt", character.conversationPrompt());
                writeOptionalProperty(writer, "conversationSettings", character.conversationSettings());
                writeOptionalProperty(writer, "movementPolicy", character.movementPolicy());
                if (character.allowWorldActions()) {
                    writeProperty(writer, "allowWorldActions", "true");
                }
            }
            try {
                rejectSymbolicLinkTarget(target);
                Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                rejectSymbolicLinkTarget(target);
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static void writeOptionalProperty(Writer writer, String key, String value) throws IOException {
        if (value != null && !value.isBlank()) {
            writeProperty(writer, key, value.trim());
        }
    }

    private static void writeProperty(Writer writer, String key, String value) throws IOException {
        writer.write(key);
        writer.write('=');
        writer.write(escapePropertyValue(value));
        writer.write('\n');
    }

    private static String escapePropertyValue(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\\' || character == '\n' || character == '\r' || character == '\t' || character == '=' || character == ':' || character == '#' || character == '!') {
                builder.append('\\');
            }
            switch (character) {
                case '\n' -> builder.append('n');
                case '\r' -> builder.append('r');
                case '\t' -> builder.append('t');
                default -> builder.append(character);
            }
        }
        return builder.toString();
    }

    private static Path safeChild(Path parent, String fileName) throws IOException {
        if (parent == null) {
            throw new IOException("Directory is unavailable");
        }
        LocalCharacterFileOperationResult fileNameValidation = validateSafeCharacterFileName(fileName);
        if (fileNameValidation != null) {
            throw new UnsafeCharacterPathException("Unsafe file name");
        }
        Path normalizedParent = parent.toAbsolutePath().normalize();
        Path child = normalizedParent.resolve(fileName).normalize();
        if (!child.startsWith(normalizedParent)) {
            throw new UnsafeCharacterPathException("File escapes directory");
        }
        return child;
    }

    private static void ensureReadableDirectory(Path path) throws IOException {
        if (path == null) {
            throw new IOException("Directory is unavailable");
        }
        rejectSymbolicLinkPath(path);
        if (Files.isSymbolicLink(path)) {
            throw new UnsafeCharacterPathException("Symbolic links are not allowed");
        }
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Directory is unavailable");
        }
    }

    private static void ensureWritableDirectory(Path path) throws IOException {
        if (path == null) {
            throw new IOException("Directory is unavailable");
        }
        rejectSymbolicLinkPath(path);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            if (Files.isSymbolicLink(path)) {
                throw new UnsafeCharacterPathException("Symbolic links are not allowed");
            }
            if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Directory is unavailable");
            }
            return;
        }
        Files.createDirectories(path);
        rejectSymbolicLinkPath(path);
        if (Files.isSymbolicLink(path)) {
            throw new UnsafeCharacterPathException("Symbolic links are not allowed");
        }
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Directory is unavailable");
        }
    }

    private static void rejectSymbolicLinkPath(Path path) throws IOException {
        Path absolutePath = path.toAbsolutePath().normalize();
        Path current = absolutePath.getRoot();
        for (Path name : absolutePath) {
            current = current == null ? name : current.resolve(name);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new UnsafeCharacterPathException("Symbolic links are not allowed");
            }
        }
    }

    private static void rejectSymbolicLinkTarget(Path target) throws IOException {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(target)) {
            throw new UnsafeCharacterPathException("Symbolic links are not allowed");
        }
    }

    private static String safeErrorMessage(List<LocalCharacterValidationError> errors, String fallback) {
        if (errors.isEmpty()) {
            return fallback;
        }
        return LocalCharacterFileOperationResult.safeDisplayText(errors.get(0).message(), 160);
    }

    private static String safeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        Path path = Path.of(fileName.replace('\\', '/')).getFileName();
        return path == null ? "" : path.toString();
    }

    private static LocalCharacterFileOperationResult rejected(String fileName, String message) {
        return new LocalCharacterFileOperationResult(LocalCharacterFileOperationStatus.REJECTED, fileName, message);
    }

    private LocalCharacterFileResult loadFile(Path file) {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            return new LocalCharacterFileResult(null, List.of(
                    new LocalCharacterValidationError(file, "Unable to read character file")
            ));
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IllegalArgumentException exception) {
            return new LocalCharacterFileResult(null, List.of(
                    new LocalCharacterValidationError(file, "Malformed character properties: " + exception.getMessage())
            ));
        } catch (IOException exception) {
            return new LocalCharacterFileResult(null, List.of(
                    new LocalCharacterValidationError(file, "Unable to read character file")
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
                    properties.getProperty("conversationSettings"),
                    Boolean.parseBoolean(properties.getProperty("allowWorldActions")),
                    properties.getProperty("movementPolicy")
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
                errors.add(new LocalCharacterValidationError(file,
                        "Unknown character field: " + LocalCharacterFileOperationResult.safeDisplayText(fieldName, 32)));
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
                properties.getProperty("conversationSettings"),
                properties.getProperty("movementPolicy")
        )) {
            errors.add(new LocalCharacterValidationError(file, message));
        }
        String allowWorldActions = properties.getProperty("allowWorldActions");
        if (allowWorldActions != null
                && !allowWorldActions.equalsIgnoreCase("true")
                && !allowWorldActions.equalsIgnoreCase("false")) {
            errors.add(new LocalCharacterValidationError(file, "allowWorldActions must be true or false"));
        }
        return List.copyOf(errors);
    }

    private record LocalCharacterFileResult(LocalCharacterDefinition character,
                                            List<LocalCharacterValidationError> errors) {
    }

    private static final class UnsafeCharacterPathException extends IOException {
        private UnsafeCharacterPathException(String message) {
            super(message);
        }
    }
}
