package dev.soffits.openplayer.character;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class LocalCharacterRepositoryWriteTest {
    private LocalCharacterRepositoryWriteTest() {
    }

    public static void main(String[] args) {
        serializesOnlyApprovedNonBlankFields();
        loadsAndSavesAllowWorldActions();
        createsAndUpdatesCharacterFiles();
        importsOnlyFromImportsDirectoryBySafeFileName();
        exportsOnlyLoadedLocalCharacters();
        rejectsUnsafeFileNamesForImportAndExport();
        rejectsUnknownImportedFields();
        rejectsNonBooleanAllowWorldActions();
        rejectsSecretLikeImportedText();
        rejectsOverwritingInvalidExistingFile();
        cleansTemporaryFilesAfterAtomicWrite();
        rejectsSymlinkedImportSourceFileIfSupported();
        rejectsSymlinkedImportDirectoryIfSupported();
        rejectsSymlinkedExistingExportTargetIfSupported();
        loadAllDoesNotLoadSymlinkedCharacterFile();
        truncatesUnknownImportedFieldNames();
        formatsFileOperationResponseWithinNetworkLimit();
    }

    private static void loadsAndSavesAllowWorldActions() {
        Path root = createTempDirectory();
        LocalCharacterRepository repository = new LocalCharacterRepository(root.resolve("characters"));
        LocalCharacterFileOperationResult result = repository.save(new LocalCharacterDefinition(
                "alex_01",
                "Alex",
                null,
                null,
                null,
                null,
                null,
                null,
                true
        ));
        require(result.succeeded(), "allowWorldActions character save must succeed: " + result);
        Properties properties = loadProperties(root.resolve("characters").resolve("alex_01.properties"));
        require("true".equals(properties.getProperty("allowWorldActions")), "true allowWorldActions must serialize");
        require(repository.loadAll().characters().get(0).allowWorldActions(), "true allowWorldActions must load");

        write(root.resolve("characters").resolve("bob_01.properties"), "id=bob_01\ndisplayName=Bob\n");
        LocalCharacterDefinition bob = repository.loadAll().characters().stream()
                .filter(character -> character.id().equals("bob_01"))
                .findFirst()
                .orElseThrow();
        require(!bob.allowWorldActions(), "missing allowWorldActions must default false");
    }

    private static void serializesOnlyApprovedNonBlankFields() {
        Path root = createTempDirectory();
        LocalCharacterRepository repository = new LocalCharacterRepository(root.resolve("characters"));
        LocalCharacterFileOperationResult result = repository.save(new LocalCharacterDefinition(
                "alex_01",
                "Alex",
                "Local helper",
                "openplayer:skins/alex",
                "skins/alex.png",
                "helper_01",
                null,
                "Friendly local preferences."
        ));
        require(result.succeeded(), "valid character save must succeed: " + result);

        Properties properties = loadProperties(root.resolve("characters").resolve("alex_01.properties"));
        require(properties.size() == 7, "blank optional fields must be omitted: " + properties.keySet());
        require("alex_01".equals(properties.getProperty("id")), "id must serialize");
        require("Alex".equals(properties.getProperty("displayName")), "displayName must serialize");
        require("Local helper".equals(properties.getProperty("description")), "description must serialize");
        require("openplayer:skins/alex".equals(properties.getProperty("skinTexture")), "skinTexture must serialize");
        require("skins/alex.png".equals(properties.getProperty("localSkinFile")), "localSkinFile must serialize");
        require("helper_01".equals(properties.getProperty("defaultRoleId")), "defaultRoleId must serialize");
        require(!properties.containsKey("conversationPrompt"), "blank conversationPrompt must be omitted");
        require("Friendly local preferences.".equals(properties.getProperty("conversationSettings")), "conversationSettings must serialize");
        for (Object key : properties.keySet()) {
            require(isApprovedField(String.valueOf(key)), "unexpected serialized field: " + key);
        }
    }

    private static void createsAndUpdatesCharacterFiles() {
        Path root = createTempDirectory();
        LocalCharacterRepository repository = new LocalCharacterRepository(root.resolve("characters"));
        require(repository.save(character("alex_01", "Alex")).status() == LocalCharacterFileOperationStatus.SAVED,
                "create must save");
        require(repository.save(character("alex_01", "Alex Updated")).status() == LocalCharacterFileOperationStatus.SAVED,
                "same id update must save");
        LocalCharacterRepositoryResult loaded = repository.loadAll();
        require(loaded.characters().size() == 1, "updated repository must contain one character");
        require("Alex Updated".equals(loaded.characters().get(0).displayName()), "update must replace character fields");
    }

    private static void importsOnlyFromImportsDirectoryBySafeFileName() {
        Path root = createTempDirectory();
        write(root.resolve("imports").resolve("bob_01.properties"), "id=bob_01\ndisplayName=Bob\n");
        write(root.resolve("bob_01.properties"), "id=bob_01\ndisplayName=Wrong\n");

        LocalCharacterRepository repository = new LocalCharacterRepository(root.resolve("characters"));
        LocalCharacterFileOperationResult result = repository.importFromDirectory(root.resolve("imports"), "bob_01.properties");
        require(result.status() == LocalCharacterFileOperationStatus.IMPORTED, "safe import must succeed: " + result);
        LocalCharacterRepositoryResult loaded = repository.loadAll();
        require(loaded.characters().size() == 1, "imported character must be saved to characters directory");
        require("Bob".equals(loaded.characters().get(0).displayName()), "import must use imports directory only");
    }

    private static void exportsOnlyLoadedLocalCharacters() {
        Path root = createTempDirectory();
        LocalCharacterRepository repository = new LocalCharacterRepository(root.resolve("characters"));
        repository.save(character("alex_01", "Alex"));

        LocalCharacterFileOperationResult result = repository.exportToDirectory(root.resolve("exports"), "alex_01");
        require(result.status() == LocalCharacterFileOperationStatus.EXPORTED, "valid export must succeed: " + result);
        Properties properties = loadProperties(root.resolve("exports").resolve("alex_01.properties"));
        require("alex_01".equals(properties.getProperty("id")), "export must write selected local character id");
        require("Alex".equals(properties.getProperty("displayName")), "export must write selected local character fields");
    }

    private static void rejectsUnsafeFileNamesForImportAndExport() {
        Path root = createTempDirectory();
        LocalCharacterRepository repository = new LocalCharacterRepository(root.resolve("characters"));
        for (String fileName : java.util.List.of(
                "../alex_01.properties",
                "/tmp/alex_01.properties",
                "C:\\alex_01.properties",
                "alex_01.txt",
                ".alex_01.properties",
                "alex\\01.properties"
        )) {
            require(repository.importFromDirectory(root.resolve("imports"), fileName).status() == LocalCharacterFileOperationStatus.REJECTED,
                    "unsafe import file name must be rejected: " + fileName);
        }
        require(repository.exportToDirectory(root.resolve("exports"), "../alex_01").status() == LocalCharacterFileOperationStatus.REJECTED,
                "unsafe export id must be rejected");
    }

    private static void rejectsUnknownImportedFields() {
        Path root = createTempDirectory();
        write(root.resolve("imports").resolve("alex_01.properties"), "id=alex_01\ndisplayName=Alex\nextra=value\n");
        LocalCharacterFileOperationResult result = new LocalCharacterRepository(root.resolve("characters"))
                .importFromDirectory(root.resolve("imports"), "alex_01.properties");
        require(result.status() == LocalCharacterFileOperationStatus.REJECTED, "unknown import fields must reject");
        require(!Files.exists(root.resolve("characters").resolve("alex_01.properties")), "rejected import must not write character file");
    }

    private static void rejectsNonBooleanAllowWorldActions() {
        Path root = createTempDirectory();
        write(root.resolve("imports").resolve("alex_01.properties"), "id=alex_01\ndisplayName=Alex\nallowWorldActions=yes\n");
        LocalCharacterFileOperationResult result = new LocalCharacterRepository(root.resolve("characters"))
                .importFromDirectory(root.resolve("imports"), "alex_01.properties");
        require(result.status() == LocalCharacterFileOperationStatus.REJECTED,
                "non-boolean allowWorldActions must reject");
        require(!Files.exists(root.resolve("characters").resolve("alex_01.properties")),
                "rejected allowWorldActions import must not write character file");
    }

    private static void rejectsSecretLikeImportedText() {
        Path root = createTempDirectory();
        write(root.resolve("imports").resolve("alex_01.properties"), "id=alex_01\ndisplayName=Alex\ndescription=contains api key marker\n");
        LocalCharacterFileOperationResult result = new LocalCharacterRepository(root.resolve("characters"))
                .importFromDirectory(root.resolve("imports"), "alex_01.properties");
        require(result.status() == LocalCharacterFileOperationStatus.REJECTED, "secret-like import text must reject");
        require(!Files.exists(root.resolve("characters").resolve("alex_01.properties")), "secret-like import must not write character file");
    }

    private static void rejectsOverwritingInvalidExistingFile() {
        Path root = createTempDirectory();
        write(root.resolve("characters").resolve("alex_01.properties"), "id=alex_01\ndisplayName=Alex\nextra=value\n");
        LocalCharacterFileOperationResult result = new LocalCharacterRepository(root.resolve("characters"))
                .save(character("alex_01", "Alex Updated"));
        require(result.status() == LocalCharacterFileOperationStatus.REJECTED, "invalid existing file must not be overwritten silently");
        Properties properties = loadProperties(root.resolve("characters").resolve("alex_01.properties"));
        require(properties.containsKey("extra"), "rejected overwrite must leave existing file untouched");
    }

    private static void cleansTemporaryFilesAfterAtomicWrite() {
        Path root = createTempDirectory();
        Path characters = root.resolve("characters");
        LocalCharacterFileOperationResult result = new LocalCharacterRepository(characters).save(character("alex_01", "Alex"));
        require(result.succeeded(), "save must succeed before temp cleanup check");
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(characters, "*.tmp")) {
            for (Path ignored : stream) {
                throw new AssertionError("temporary character files must be cleaned up");
            }
        } catch (IOException exception) {
            throw new AssertionError("unable to list temp files", exception);
        }
    }

    private static void rejectsSymlinkedImportSourceFileIfSupported() {
        Path root = createTempDirectory();
        Path external = root.resolve("external.properties");
        write(external, "id=bob_01\ndisplayName=Bob\n");
        Path source = root.resolve("imports").resolve("bob_01.properties");
        if (!tryCreateSymbolicLink(source, external)) {
            return;
        }

        LocalCharacterFileOperationResult result = new LocalCharacterRepository(root.resolve("characters"))
                .importFromDirectory(root.resolve("imports"), "bob_01.properties");
        require(result.status() == LocalCharacterFileOperationStatus.REJECTED, "symlinked import source must reject");
        require(!Files.exists(root.resolve("characters").resolve("bob_01.properties")), "symlinked import source must not write character file");
    }

    private static void rejectsSymlinkedImportDirectoryIfSupported() {
        Path root = createTempDirectory();
        Path externalImports = root.resolve("external-imports");
        write(externalImports.resolve("bob_01.properties"), "id=bob_01\ndisplayName=Bob\n");
        Path imports = root.resolve("imports");
        if (!tryCreateSymbolicLink(imports, externalImports)) {
            return;
        }

        LocalCharacterFileOperationResult result = new LocalCharacterRepository(root.resolve("characters"))
                .importFromDirectory(imports, "bob_01.properties");
        require(result.status() == LocalCharacterFileOperationStatus.REJECTED, "symlinked import directory must reject");
        require(!Files.exists(root.resolve("characters").resolve("bob_01.properties")), "symlinked import directory must not write character file");
    }

    private static void rejectsSymlinkedExistingExportTargetIfSupported() {
        Path root = createTempDirectory();
        LocalCharacterRepository repository = new LocalCharacterRepository(root.resolve("characters"));
        require(repository.save(character("alex_01", "Alex")).succeeded(), "save must succeed before symlink export test");
        Path external = root.resolve("external.properties");
        write(external, "id=outside\ndisplayName=Outside\n");
        Path target = root.resolve("exports").resolve("alex_01.properties");
        if (!tryCreateSymbolicLink(target, external)) {
            return;
        }

        LocalCharacterFileOperationResult result = repository.exportToDirectory(root.resolve("exports"), "alex_01");
        require(result.status() == LocalCharacterFileOperationStatus.REJECTED, "symlinked export target must reject");
        Properties properties = loadProperties(external);
        require("outside".equals(properties.getProperty("id")), "symlinked export target must not overwrite external file");
    }

    private static void loadAllDoesNotLoadSymlinkedCharacterFile() {
        Path root = createTempDirectory();
        Path external = root.resolve("external.properties");
        write(external, "id=mallory_01\ndisplayName=Mallory\n");
        Path characterFile = root.resolve("characters").resolve("mallory_01.properties");
        if (!tryCreateSymbolicLink(characterFile, external)) {
            return;
        }

        LocalCharacterRepositoryResult result = new LocalCharacterRepository(root.resolve("characters")).loadAll();
        require(result.characters().isEmpty(), "loadAll must not load symlinked character file");
    }

    private static void truncatesUnknownImportedFieldNames() {
        Path root = createTempDirectory();
        String hugeFieldName = "unknown" + "x".repeat(512);
        write(root.resolve("imports").resolve("alex_01.properties"), "id=alex_01\ndisplayName=Alex\n" + hugeFieldName + "=value\n");
        LocalCharacterFileOperationResult result = new LocalCharacterRepository(root.resolve("characters"))
                .importFromDirectory(root.resolve("imports"), "alex_01.properties");

        require(result.status() == LocalCharacterFileOperationStatus.REJECTED, "unknown import field must reject");
        require(!result.message().contains(hugeFieldName), "unknown field message must not include huge raw key");
        require(result.message().length() <= 160, "unknown field message must be bounded");
    }

    private static void formatsFileOperationResponseWithinNetworkLimit() {
        String hugeFileName = "file" + "x".repeat(512) + ".properties";
        String hugeMessage = "message" + "y".repeat(512);
        LocalCharacterFileOperationResult result = new LocalCharacterFileOperationResult(
                LocalCharacterFileOperationStatus.REJECTED,
                hugeFileName,
                hugeMessage
        );

        require(result.formatForClientStatus().length() <= LocalCharacterFileOperationResult.NETWORK_RESPONSE_MAX_LENGTH,
                "file operation response must fit the network limit");
        require(!result.formatForClientStatus().contains(hugeFileName), "file operation response must not include huge raw file name");
        require(!result.formatForClientStatus().contains(hugeMessage), "file operation response must not include huge raw message");
    }

    private static LocalCharacterDefinition character(String id, String displayName) {
        return new LocalCharacterDefinition(id, displayName, null, null, null, null, null, null);
    }

    private static boolean isApprovedField(String fieldName) {
        return java.util.Set.of(
                "id",
                "displayName",
                "description",
                "skinTexture",
                "localSkinFile",
                "defaultRoleId",
                "conversationPrompt",
                "conversationSettings",
                "allowWorldActions"
        ).contains(fieldName);
    }

    private static Properties loadProperties(Path file) {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException exception) {
            throw new AssertionError("unable to load properties", exception);
        }
        return properties;
    }

    private static Path createTempDirectory() {
        try {
            return Files.createTempDirectory("openplayer-character-write-test");
        } catch (IOException exception) {
            throw new AssertionError("unable to create temp directory", exception);
        }
    }

    private static void write(Path file, String value) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, value);
        } catch (IOException exception) {
            throw new AssertionError("unable to write test file", exception);
        }
    }

    private static boolean tryCreateSymbolicLink(Path link, Path target) {
        try {
            Files.createDirectories(link.getParent());
            Files.createSymbolicLink(link, target);
            return true;
        } catch (IOException | SecurityException | UnsupportedOperationException exception) {
            return false;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
